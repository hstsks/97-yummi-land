import {normalizeBlock} from './registry.mjs';
export const block=normalizeBlock;
export class Voxel {
  constructor(id,size,origin) {this.id=id;this.size=[...size];[this.W,this.H,this.L]=size;this.origin=[...origin];this.meta={};this.cells=new Array(this.W*this.H*this.L).fill(block('air'));}
  inside(x,y,z){return Number.isInteger(x)&&Number.isInteger(y)&&Number.isInteger(z)&&x>=0&&y>=0&&z>=0&&x<this.W&&y<this.H&&z<this.L;}
  index(x,y,z){return (y*this.L+z)*this.W+x;}
  set(x,y,z,state) {if(!this.inside(x,y,z)) throw new Error(`${this.id}: out of bounds ${x},${y},${z}`);this.cells[this.index(x,y,z)]=typeof state==='string'?block(state):state;return this;}
  get(x,y,z){return this.inside(x,y,z)?this.cells[this.index(x,y,z)]:undefined;}
  fill(x0,y0,z0,x1,y1,z1,state){const s=typeof state==='string'?block(state):state;for(let y=y0;y<=y1;y++)for(let z=z0;z<=z1;z++)for(let x=x0;x<=x1;x++)this.set(x,y,z,s);return this;}
}
