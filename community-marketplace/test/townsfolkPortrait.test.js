import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";
import sharp from "sharp";
import { renderPortrait, createPortraitRenderer, TOWNSFOLK_ICON } from "../server/townsfolkPortrait.js";

test("portrait uses the townsfolk camera with depth, transparent edges and skin gradients", async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "townsfolk-portrait-"));
  const write = (name, value) => { const p = path.join(dir,name);fs.mkdirSync(path.dirname(p),{recursive:true});fs.writeFileSync(p,JSON.stringify(value)); };
  try {
    write("Cosmetics/CharacterCreator/GradientSets.json",[{Id:"Skin",Gradients:{Test:{Texture:"Characters/ramp.png"}}}]);
    const layout = Object.fromEntries(["front","back","left","right","top","bottom"].map(side=>[side,{offset:{x:0,y:0}}]));
    write("Common/Characters/Test.blockymodel",{nodes:[{name:"Head",position:{x:0,y:110,z:0},shape:{type:"box",settings:{size:{x:24,y:24,z:24}},textureLayout:layout}}]});
    await sharp({create:{width:24,height:24,channels:4,background:{r:128,g:128,b:128,alpha:1}}}).png().toFile(path.join(dir,"Common/Characters/skin.png"));
    await sharp({create:{width:256,height:1,channels:4,background:{r:200,g:100,b:50,alpha:1}}}).png().toFile(path.join(dir,"Common/Characters/ramp.png"));
    const model={Model:"Characters/Test.blockymodel",Texture:"Characters/skin.png",GradientSet:"Skin",GradientId:"Test",DefaultAttachments:[]};
    const png=await createPortraitRenderer([dir])(model), {data,info}=await sharp(png).raw().toBuffer({resolveWithObject:true});
    assert.equal(info.width,128);assert.equal(info.height,128);
    assert.deepEqual(TOWNSFOLK_ICON,{Scale:.5,Rotation:[0,19,0],Translation:[0,-110]});
    assert.deepEqual([...data.subarray((64*128+64)*4,(64*128+64)*4+4)],[200,100,50,255]);
    assert.equal(data[3],0);
    const columns=[];
    for(let x=0;x<128;x++) if(data[(64*128+x)*4+3]) columns.push(x);
    // A 24-unit cube at 19 degrees is wider than a front-on 48px square.
    assert.ok(columns.length >= 60 && columns.length <= 64, String(columns.length));
    await assert.rejects(renderPortrait({...model,Model:"../../secret.blockymodel"},[dir]),/Invalid portrait asset path/);
  } finally { fs.rmSync(dir,{recursive:true,force:true}); }
});

const repo=path.resolve(path.dirname(fileURLToPath(import.meta.url)),"../..");
const native=path.resolve(repo,"../HytaleSourceCode/hytale-shared-source/HytaleAssets");
test("real submitted appearance matches the existing LoneStar144 portrait framing", {skip:!fs.existsSync(path.join(native,"Cosmetics/CharacterCreator/GradientSets.json"))}, async () => {
  const model=JSON.parse(fs.readFileSync(path.join(repo,"src/main/resources/Server/Models/Townsfolk/LoneStar144.json"),"utf8"));
  const actual=await renderPortrait(model,[path.join(repo,"src/main/resources"),native]);
  const a=await sharp(actual).ensureAlpha().raw().toBuffer();
  const b=await sharp(path.join(repo,"src/main/resources/Common/Icons/ModelsGenerated/LoneStar144.png")).ensureAlpha().raw().toBuffer();
  let union=0,intersection=0,difference=0;
  for(let i=0;i<a.length;i++) {
    difference+=Math.abs(a[i]-b[i]);
    if(i%4===3) {if(a[i]||b[i])union++;if(a[i]&&b[i])intersection++;}
  }
  assert.ok(intersection/union>.995,`silhouette overlap ${intersection/union}`);
  assert.ok(difference/a.length<2,`mean channel difference ${difference/a.length}`);
});
