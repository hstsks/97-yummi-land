import fs from 'node:fs';
import zlib from 'node:zlib';
const codes={end:0,byte:1,short:2,int:3,long:4,float:5,double:6,byteArray:7,string:8,list:9,compound:10,intArray:11,longArray:12};
const names=Object.fromEntries(Object.entries(codes).map(([k,v])=>[v,k]));
const utf=s=>{const b=Buffer.from(s,'utf8'),n=Buffer.alloc(2);n.writeUInt16BE(b.length);return Buffer.concat([n,b]);};
const int=v=>{const b=Buffer.alloc(4);b.writeInt32BE(v);return b;};
function payload(t,v){
  switch(t){
    case 'int': return int(v);
    case 'string':return utf(v);
    case 'compound':return Buffer.concat([...Object.entries(v).map(([n,t])=>Buffer.concat([Buffer.from([codes[t.type]]),utf(n),payload(t.type,t.value)])),Buffer.from([0])]);
    case 'list':return Buffer.concat([Buffer.from([codes[v.type]]),int(v.value.length),...v.value.map(x=>payload(v.type,x))]);
    default:throw new Error(`Writer unsupported type ${t}`);
  }
}
export function encodeStructure(voxel){
  const palette=[],lookup=new Map(),blocks=[];
  for(let y=0;y<voxel.H;y++)for(let z=0;z<voxel.L;z++)for(let x=0;x<voxel.W;x++){
    const s=voxel.get(x,y,z),key=JSON.stringify(s);let state=lookup.get(key);
    if(state===undefined){state=palette.length;lookup.set(key,state);palette.push({Name:{type:'string',value:s.Name},...(s.Properties?{Properties:{type:'compound',value:Object.fromEntries(Object.entries(s.Properties).map(([k,v])=>[k,{type:'string',value:v}]))}}:{})});}
    blocks.push({pos:{type:'list',value:{type:'int',value:[x,y,z]}},state:{type:'int',value:state}});
  }
  const root={DataVersion:{type:'int',value:4189},size:{type:'list',value:{type:'int',value:voxel.size}},palette:{type:'list',value:{type:'compound',value:palette}},blocks:{type:'list',value:{type:'compound',value:blocks}},entities:{type:'list',value:{type:'compound',value:[]}}};
  return zlib.gzipSync(Buffer.concat([Buffer.from([10]),utf(''),payload('compound',root)]),{level:9,mtime:0});
}
// This decoder reads the serialized byte stream; it does not use the generator's grid.
export function decodeNBT(compressed){
  const buf=zlib.gunzipSync(compressed);let offset=0;
  const need=n=>{if(n<0||offset+n>buf.length)throw new Error('Truncated NBT');};
  const u8=()=>{need(1);return buf[offset++];};
  const i32=()=>{need(4);const n=buf.readInt32BE(offset);offset+=4;return n;};
  const str=()=>{need(2);const n=buf.readUInt16BE(offset);offset+=2;need(n);const s=buf.toString('utf8',offset,offset+n);offset+=n;return s;};
  function read(type,depth=0){
    if(depth>64)throw new Error('NBT nesting limit');
    if(type==='byte'){need(1);return buf.readInt8(offset++);}
    if(type==='short'){need(2);const n=buf.readInt16BE(offset);offset+=2;return n;}
    if(type==='int')return i32();
    if(type==='long'){need(8);const n=buf.readBigInt64BE(offset);offset+=8;return n;}
    if(type==='float'||type==='double'){const n=type==='float'?4:8;need(n);const value=type==='float'?buf.readFloatBE(offset):buf.readDoubleBE(offset);offset+=n;return value;}
    if(type==='string')return str();
    if(type==='compound'){const result={};while(true){const code=u8();if(code===0)break;if(!names[code])throw new Error('Unknown NBT type');const name=str();if(Object.hasOwn(result,name))throw new Error('Duplicate compound key');result[name]={type:names[code],value:read(names[code],depth+1)};}return result;}
    if(type==='list'){const subtype=names[u8()],count=i32();if(!subtype||count<0||count>2000000||(subtype==='end'&&count))throw new Error('Invalid list');return {type:subtype,value:Array.from({length:count},()=>read(subtype,depth+1))};}
    if(type==='byteArray'||type==='intArray'||type==='longArray'){const count=i32();if(count<0||count>2000000)throw new Error('Invalid array');return Array.from({length:count},()=>read(type==='byteArray'?'byte':type==='intArray'?'int':'long',depth+1));}
    throw new Error(`Unsupported NBT type ${type}`);
  }
  const type=names[u8()],name=str();if(type!=='compound')throw new Error('Structure root must be compound');
  const root={type,name,value:read(type)};if(offset!==buf.length)throw new Error('Trailing NBT bytes');return root;
}
export function simplify(tag){if(tag.type==='compound')return Object.fromEntries(Object.entries(tag.value).map(([k,v])=>[k,simplify(v)]));if(tag.type==='list')return tag.value.value.map(value=>simplify({type:tag.value.type,value}));return tag.value;}
export function readStructure(file){
  const root=decodeNBT(Buffer.isBuffer(file)?file:fs.readFileSync(file)),plain=simplify(root),{size,palette,blocks}=plain;
  const [W,H,L]=size,grid=new Int32Array(W*H*L).fill(-1);
  for(const {pos:[x,y,z],state} of blocks)if(x>=0&&x<W&&y>=0&&y<H&&z>=0&&z<L)grid[(y*L+z)*W+x]=state;
  return {root,...plain,grid,get(x,y,z){return x>=0&&x<W&&y>=0&&y<H&&z>=0&&z<L?palette[grid[(y*L+z)*W+x]]:undefined;}};
}
