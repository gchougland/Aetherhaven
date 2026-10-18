import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { renderWikiTopic, listWikiTopicIds, buildSitemapXml } from '../server/wikiRender.js';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const source=path.resolve(root,'../src/main/resources/Server/Aetherhaven/GuideTopics/en-US');
const wiki=path.join(root,'web/wiki');
const nav=JSON.parse(fs.readFileSync(path.join(wiki,'nav.json'),'utf8'));
const search=JSON.parse(fs.readFileSync(path.join(wiki,'search-index.json'),'utf8'));

test('public wiki mirrors the current journal source, including all workplace and system guides',()=>{
  for(const file of fs.readdirSync(source).filter(f=>f.endsWith('.md')&&f!=='villager_halloween.md')) {
    const id=file.slice(0,-3);
    assert.equal(fs.readFileSync(path.join(wiki,'topics',file),'utf8'),fs.readFileSync(path.join(source,file),'utf8'),`Run sync-wiki: ${id}`);
    assert.ok(nav.flat.some(page=>page.id===id),`Not in website navigation: ${id}`);
    assert.ok(search.some(page=>page.id===id),`Not searchable: ${id}`);
  }
});

test('every navigation entry renders with real content and any portrait has a shipped image',()=>{
  const visited=new Set();
  for(const entry of nav.flat) {
    assert.ok(!visited.has(entry.id),`Duplicate navigation: ${entry.id}`);visited.add(entry.id);
    const page=renderWikiTopic(entry.id);
    assert.ok(page?.html.length>100,`Empty or missing page: ${entry.id}`);
    assert.ok(!page.html.includes('sub-topics:'),`Frontmatter leaked into ${entry.id}`);
    for(const image of page.html.matchAll(/src="\/wiki\/images\/([^"?]+)/g)) {
      assert.ok(fs.existsSync(path.join(wiki,'images',image[1])),`Missing image in ${entry.id}: ${image[1]}`);
    }
  }
});

test('the hidden resident is absent from public navigation, topics, search and sitemap',()=>{
  assert.ok(!nav.flat.some(page=>page.id==='villager_halloween'));
  assert.ok(!search.some(page=>page.id==='villager_halloween'));
  assert.ok(!listWikiTopicIds().includes('villager_halloween'));
  assert.ok(!buildSitemapXml('https://example.test').includes('villager_halloween'));
});
