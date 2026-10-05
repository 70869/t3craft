#!/usr/bin/env node
// Read-only T3 QA: traverse every physical wall page using Minecraft's real use-key path.
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { DevClient } from './lib/devclient.mjs';

const dev=await DevClient.connect({port:Number(process.argv[2]||27879),timeoutMs:10000});
const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
let wallEye;
const wall=async()=>{
  const result=await dev.call('dev.taskwall');
  const board=result.boards.find(board=>board.size==='7x4');
  assert(board,'render the HQ wall before running this check');
  return board;
};
const click=async aim=>{
  await dev.call('dev.camera',{...wallEye,lookAt:aim.point,mode:'creative',hideHud:false});
  await sleep(150);
  await dev.call('dev.key',{mapping:'key.use'});
  await sleep(150);
};
const turn=async(lane,previousPage,origin)=>{
  const aim=await dev.call('dev.taskwall',{aimPage:lane,previousPage,board:origin});
  await click(aim.aim);
};
try {
  await dev.call('dev.screen',{open:null});
  const view=await dev.call('dev.camera',{anchor:'cam_task_wall',mode:'creative',hideHud:false});
  wallEye={x:view.camera.x,y:view.camera.y,z:view.camera.z};
  await sleep(200);
  const first=await wall();
  assert.deepEqual(Object.values(first.columns).map(col=>col.label),['Idle','Working','Needs you','Done']);
  const total=Object.values(first.columns).reduce((sum,col)=>sum+col.count,0);
  assert.equal(first.cards.length,total,'every chat retained in the physical board');
  const evidence={total,lanes:[]};
  for(const [lane,col] of Object.entries(first.columns)) {
    if(col.pages<=1) continue;
    let board=await wall();
    while(board.columns[lane].page>0) { await turn(lane,true,first.origin); board=await wall(); }
    await turn(lane,true,first.origin);
    assert.equal((await wall()).columns[lane].page,0,'previous at first page is consumed safely');
    const seen=new Set();
    for(let page=0;page<col.pages;page++) {
      board=await wall();
      assert.equal(board.columns[lane].page,page,'physical click advances exactly one page');
      for(const card of board.cards.filter(card=>card.col===lane&&card.visible)) {
        assert.equal(card.size,'full'); assert(card.h>=53); assert(card.lines.length>0);
        assert(!seen.has(card.id),'page traversal cannot duplicate a chat'); seen.add(card.id);
      }
      if(page+1<col.pages) await turn(lane,false,first.origin);
    }
    assert.equal(seen.size,col.count,'every chat reachable through physical page controls');
    await turn(lane,false,first.origin);
    assert.equal((await wall()).columns[lane].page,col.pages-1,'next at last page is consumed safely');
    evidence.lanes.push({lane,count:col.count,pages:col.pages,reached:seen.size});
    if(col.pages>1) { await turn(lane,true,first.origin); assert.equal((await wall()).columns[lane].page,col.pages-2,'previous page works'); }
  }
  const final=await wall(); const card=final.cards.find(card=>card.visible);
  assert(card); const aimed=await dev.call('dev.taskwall',{aim:card.id,board:final.origin});
  await click(aimed.aim);
  const state=await dev.call('dev.state'); assert.equal(state.screen?.class,'dev.agentcraft.client.t3.T3TaskScreen','physical card opens native chat card');
  evidence.cardScreen=state.screen.class;
  await dev.call('dev.screen',{open:null});
  await mkdir('artifacts/qa',{recursive:true});
  await writeFile('artifacts/qa/t3-wall.json',JSON.stringify(evidence,null,2)+'\n');
  console.log(JSON.stringify({ok:true,...evidence}));
} finally { dev.close(); }
