import fs from "node:fs/promises";
import path from "node:path";
import { Worker, isMainThread, parentPort, workerData } from "node:worker_threads";
import sharp from "sharp";

// Same orthographic camera, rest pose, texels and framing as tools/render_villager_portrait.py --icon.
// Townsfolk use 19 degrees; the 33-degree preset belongs to the later core villagers.
export const TOWNSFOLK_ICON = Object.freeze({ Scale: 0.5, Rotation: [0, 19, 0], Translation: [0, -110] });
const I = [[1, 0, 0], [0, 1, 0], [0, 0, 1]], ZERO = [0, 0, 0];
const vec = (v = {}) => [v.x || 0, v.y || 0, v.z || 0];
const add = (a, b) => a.map((v, i) => v + b[i]);
const sub = (a, b) => a.map((v, i) => v - b[i]);
const mv = (m, v) => m.map(row => row.reduce((s, x, i) => s + x * v[i], 0));
const mm = (a, b) => a.map(row => b[0].map((_, j) => row.reduce((s, x, i) => s + x * b[i][j], 0)));
function rotation(q = {}) {
  let { x = 0, y = 0, z = 0, w = 1 } = q;
  const n = Math.hypot(x, y, z, w); x /= n; y /= n; z /= n; w /= n;
  return [[1-2*(y*y+z*z),2*(x*y-z*w),2*(x*z+y*w)], [2*(x*y+z*w),1-2*(x*x+z*z),2*(y*z-x*w)], [2*(x*z-y*w),2*(y*z+x*w),1-2*(x*x+y*y)]];
}
function points(shape) {
  const size = shape.settings.size, stretch = shape.stretch || {};
  const scaled = v => v.map((x, i) => x * (stretch["xyz"[i]] ?? 1));
  if (shape.type === "box") return [-.5,.5].flatMap(x => [-.5,.5].flatMap(y => [-.5,.5].map(z => scaled([x*size.x,y*size.y,z*size.z]))));
  const axis = (shape.settings.normal || "+Z").slice(-1);
  const u = axis === "X" ? [0,0,1] : [1,0,0], v = axis === "Y" ? [0,0,1] : [0,1,0];
  return [[-.5,-.5],[.5,-.5],[.5,.5],[-.5,.5]].map(([x,y]) => scaled(u.map((a,i) => a*x*size.x+v[i]*y*size.y)));
}
const BOX = [["left",[2,3,1,0],"z","y"],["right",[7,6,4,5],"z","y"],["bottom",[1,5,4,0],"x","z"],["top",[2,6,7,3],"x","z"],["back",[6,2,0,4],"x","y"],["front",[3,7,5,1],"x","y"]];
const QUAD = [["front",[3,2,1,0],"x","y"]];

/** Reads only local, synced game assets. Submitted JSON never supplies geometry, URLs or filesystem roots. */
export async function renderPortrait(model, assetRoots) {
  model = { Model: "Characters/Player.blockymodel", Texture: "Characters/Player_Textures/Player_Greyscale.png", GradientSet: "Skin", GradientId: "02", ...model };
  async function read(relative) {
    if (!/^[A-Za-z0-9_./-]+$/.test(relative) || relative.includes("..") || relative.startsWith("/")) throw new Error("Invalid portrait asset path");
    for (const root of assetRoots) {
      try { return await fs.readFile(path.join(root, relative)); } catch (e) { if (e.code !== "ENOENT") throw e; }
    }
    throw new Error(`Portrait asset is missing: ${relative}`);
  }
  const json = async relative => JSON.parse(await read(relative));
  const gradients = Object.fromEntries((await json("Cosmetics/CharacterCreator/GradientSets.json")).map(g => [g.Id, g.Gradients]));
  async function texture(part) {
    const { data, info } = await sharp(await read("Common/" + part.Texture)).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
    const gradient = gradients[part.GradientSet]?.[part.GradientId];
    if (part.GradientSet && !gradient) throw new Error("Portrait gradient is missing");
    if (gradient) {
      const ramp = await sharp(await read("Common/" + gradient.Texture)).resize(256, 1, { kernel: "linear" }).removeAlpha().raw().toBuffer();
      for (let i=0; i<data.length; i+=4) if (data[i] === data[i+1] && data[i+1] === data[i+2]) {
        const at=data[i]*3; data[i]=ramp[at]; data[i+1]=ramp[at+1]; data[i+2]=ramp[at+2];
      }
    }
    return { data, width: info.width, height: info.height };
  }
  const base = await json("Common/" + model.Model), bones = new Map(), parts = [];
  function walk(nodes, parentR, parentP, anchors, attach, tex) {
    for (const node of nodes) {
      const shape = node.shape || {};
      let r, p;
      if (attach && (shape.type || "none") === "none" && shape.settings?.isPiece && bones.has(node.name)) [r,p] = bones.get(node.name);
      else {
        r=mm(parentR,rotation(node.orientation)); p=add(add(parentP,mv(parentR,vec(node.position))),mv(r,vec(shape.offset)));
        if (!anchors.has(node.name)) anchors.set(node.name,[r,p]);
        if (shape.visible !== false && ["box","quad"].includes(shape.type)) parts.push({shape, points:points(shape).map(v=>add(mv(r,v),p)),tex});
      }
      walk(node.children || [],r,p,anchors,attach,tex);
    }
  }
  walk(base.nodes,I,ZERO,bones,false,await texture(model));
  for (const part of model.DefaultAttachments) {
    const anchors=new Map();
    walk((await json("Common/"+part.Model)).nodes,I,ZERO,anchors,true,await texture(part));
    for (const [name, frame] of anchors) if (!bones.has(name)) bones.set(name,frame);
  }
  const extent=512, pixels=Buffer.alloc(extent*extent*4), depth=new Float64Array(extent*extent).fill(-1e9);
  const yaw=TOWNSFOLK_ICON.Rotation[1]*Math.PI/180, cam=[[Math.cos(yaw),0,Math.sin(yaw)],[0,1,0],[-Math.sin(yaw),0,Math.cos(yaw)]];
  const perUnit=4*TOWNSFOLK_ICON.Scale*extent/128;
  for (const part of parts) for (const [side,corners,across,down] of part.shape.type === "quad" ? QUAD : BOX) {
    const layout=part.shape.textureLayout?.[side], size=part.shape.settings.size;
    const width=Math.trunc(size[across]), height=Math.trunc(size[down] ?? 1);
    if (!layout || width<1 || height<1) continue;
    const quad=corners.map(i=>part.points[i]), origin=mv(cam,quad[0]), a3=mv(cam,sub(quad[1],quad[0])), b3=mv(cam,sub(quad[3],quad[0]));
    const o=[extent/2+origin[0]*perUnit+2,extent/2-(origin[1]-110)*perUnit+2];
    const a=[a3[0]*perUnit,-a3[1]*perUnit], b=[b3[0]*perUnit,-b3[1]*perUnit], det=a[0]*b[1]-a[1]*b[0];
    if (Math.abs(det)<1e-6) continue;
    const xs=[o[0],o[0]+a[0],o[0]+b[0],o[0]+a[0]+b[0]], ys=[o[1],o[1]+a[1],o[1]+b[1],o[1]+a[1]+b[1]];
    const x0=Math.max(0,Math.floor(Math.min(...xs))),x1=Math.min(extent,Math.ceil(Math.max(...xs)));
    const y0=Math.max(0,Math.floor(Math.min(...ys))),y1=Math.min(extent,Math.ceil(Math.max(...ys)));
    const turn=(layout.angle||0)*Math.PI/180, c=Math.cos(turn),s=Math.sin(turn);
    for (let y=y0;y<y1;y++) for (let x=x0;x<x1;x++) {
      const px=x+.5-o[0],py=y+.5-o[1],u=(px*b[1]-py*b[0])/det,v=(py*a[0]-px*a[1])/det;
      if (u<0||u>=1||v<0||v>=1) continue;
      const z=origin[2]+u*a3[2]+v*b3[2], at=y*extent+x;
      if (z<=depth[at]) continue;
      const ux=(Math.floor(u*width)+.5)*(layout.mirror?.x?-1:1),vy=(Math.floor(v*height)+.5)*(layout.mirror?.y?-1:1);
      const tx=Math.floor(c*ux-s*vy+layout.offset.x+1e-7),ty=Math.floor(s*ux+c*vy+layout.offset.y+1e-7);
      if (tx<0||ty<0||tx>=part.tex.width||ty>=part.tex.height) continue;
      const source=(ty*part.tex.width+tx)*4;
      if (part.tex.data[source+3]===0) continue;
      part.tex.data.copy(pixels,at*4,source,source+3); pixels[at*4+3]=255; depth[at]=z;
    }
  }
  // Box average with premultiplied alpha, matching Pillow BOX and avoiding dark edge halos.
  const output=Buffer.alloc(128*128*4);
  for (let y=0;y<128;y++) for(let x=0;x<128;x++) {
    const sums=[0,0,0]; let count=0;
    for(let dy=0;dy<4;dy++) for(let dx=0;dx<4;dx++) {
      const at=((y*4+dy)*512+x*4+dx)*4;
      if(pixels[at+3]) { count++; for(let c=0;c<3;c++) sums[c]+=pixels[at+c]; }
    }
    const at=(y*128+x)*4;
    if(count) for(let c=0;c<3;c++) output[at+c]=Math.round(sums[c]/count);
    output[at+3]=Math.round(255*count/16);
  }
  return sharp(output,{raw:{width:128,height:128,channels:4}}).png().toBuffer();
}

let queue=Promise.resolve();
/** Serial, bounded worker work keeps rendering off the web server's event loop. */
export function createPortraitRenderer(assetRoots) {
  return model => {
    const task=queue.catch(()=>{}).then(()=>new Promise((resolve,reject)=>{
      const worker=new Worker(new URL(import.meta.url),{workerData:{model,assetRoots}});
      const timer=setTimeout(()=>{ void worker.terminate(); reject(new Error("Portrait rendering timed out")); },45000);
      worker.once("message",message=>{ clearTimeout(timer); message.error?reject(new Error(message.error)):resolve(Buffer.from(message.png)); });
      worker.once("error",error=>{clearTimeout(timer);reject(error);});
      worker.once("exit",code=>{clearTimeout(timer);if(code!==0)reject(new Error("Portrait worker stopped"));});
    }));
    queue=task; return task;
  };
}
if (!isMainThread) {
  try { parentPort.postMessage({png:await renderPortrait(workerData.model,workerData.assetRoots)}); }
  catch(error) { parentPort.postMessage({error:error.message}); }
}
