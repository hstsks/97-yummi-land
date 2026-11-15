"""JSON/PNG review. Use --prepare, then --render-saved in a fresh Blender process.
Run tools/render-v3.ps1 to serialize the complete workflow. Context rigs are
approximations; they do not reproduce enclosing Minecraft player/entity poses.
"""
import argparse, hashlib, json, math, os, sys
from pathlib import Path
import bpy
from mathutils import Matrix, Vector

ROOT=Path(__file__).resolve().parents[3]
OUT=ROOT/'resourcepack-gen/overrides/assets/yuumi97'
PROD=ROOT/'assets/yuumi97-production'
AXIS=Matrix(((1,0,0),(0,0,-1),(0,1,0)))
AXIS4=AXIS.to_4x4()
VIEWS={'front':(0,6,.4),'side':(6,0,.4),'rear':(0,-6,.4),'threequarter':(4,6,3.4)}
HANDS=['thirdperson_righthand','thirdperson_lefthand','firstperson_righthand','firstperson_lefthand']
NOTICE='Blender review; Minecraft loading, lighting and enclosing player/entity transforms require client verification.'

def convert(p): return AXIS@((Vector(p)-Vector((8,8,8)))/16)
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def model_path(model_id):
    if not model_id or any(c not in 'abcdefghijklmnopqrstuvwxyz0123456789_' for c in model_id): raise ValueError('Invalid model ID')
    return OUT/'models/item'/f'{model_id}.json'
def write_json(p,data):
    p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(json.dumps(data,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')

def configure_cycles(scene,request):
    scene.render.engine='CYCLES';scene.cycles.samples=20;scene.cycles.use_denoising=True;scene.cycles.device='CPU'
    result={'requested':request,'device':'CPU','backend':None,'devices':[]}
    addon=bpy.context.preferences.addons.get('cycles')
    if request!='CPU' and addon:
        prefs=addon.preferences
        for backend in ['OPTIX','CUDA','HIP','ONEAPI','METAL']:
            try:
                prefs.compute_device_type=backend;prefs.get_devices()
                found=[d for d in prefs.devices if d.type==backend]
                if not found: continue
                for d in prefs.devices:d.use=d in found
                scene.cycles.device='GPU'
                result.update(device='GPU',backend=backend,devices=[d.name for d in found]);break
            except (TypeError,RuntimeError): pass
    if request=='GPU' and result['device']!='GPU':raise RuntimeError('No usable Cycles GPU. Use CPU or AUTO.')
    scene['render_device']=json.dumps(result)
    return result

def bounds(objects):
    pts=[o.matrix_world@v.co for o in objects for v in o.data.vertices]
    if not pts:raise ValueError('Empty model')
    return Vector(tuple(min(p[i] for p in pts) for i in range(3))),Vector(tuple(max(p[i] for p in pts) for i in range(3)))

def frame(scene,objects,direction=(4,6,3.4)):
    bpy.context.view_layer.update();lo,hi=bounds(objects);center=(lo+hi)/2;span=max(max(hi-lo),.25)
    camera=scene.camera;camera.data.type='ORTHO';camera.data.ortho_scale=span*1.52
    camera.location=center+Vector(direction)*max(span,1)
    camera.rotation_euler=(center-camera.location).to_track_quat('-Z','Y').to_euler()
    for o in scene.objects:
        if o.type=='LIGHT' and 'review_direction' in o:
            o.location=center+Vector(o['review_direction'])*max(span,.8)/2
            o.rotation_euler=(center-o.location).to_track_quat('-Z','Y').to_euler()
    return lo,hi

def create_model(model_id,device):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    data=json.loads(model_path(model_id).read_text(encoding='utf-8'))
    scene=bpy.context.scene;configure_cycles(scene,device)
    scene.render.resolution_x=640;scene.render.resolution_y=640;scene.render.resolution_percentage=100
    scene.render.image_settings.file_format='PNG';scene.render.image_settings.color_mode='RGBA';scene.render.film_transparent=False
    scene.view_settings.view_transform='Standard';scene.view_settings.look='None'
    # White chef cloth needs one lower review stop to retain its actual modeled folds.
    # This changes only the review lighting; delivered atlas colors are untouched.
    scene.view_settings.exposure=-1.7 if model_id=='hat_chef_toque' else -.7
    world=bpy.data.worlds.new('Neutral studio');world.use_nodes=True
    nodes=world.node_tree.nodes;nodes.clear();background=nodes.new('ShaderNodeBackground');output=nodes.new('ShaderNodeOutputWorld')
    background.inputs[0].default_value=(.17,.19,.23,1);background.inputs[1].default_value=.45
    world.node_tree.links.new(background.outputs[0],output.inputs[0]);scene.world=world
    materials={};hashes={str(model_path(model_id)):sha(model_path(model_id))}
    def material(ref):
        seen=set()
        while ref.startswith('#'):
            if ref in seen:raise ValueError('Cyclic texture alias')
            seen.add(ref);ref=data['textures'][ref[1:]]
        if ref in materials:return materials[ref]
        namespace,local=ref.split(':',1)
        png=ROOT/'resourcepack-gen/overrides/assets'/namespace/'textures'/f'{local}.png'
        hashes[str(png)]=sha(png);image=bpy.data.images.load(str(png),check_existing=True);image.pack()
        mat=bpy.data.materials.new(ref);mat.use_nodes=True;nodes=mat.node_tree.nodes;nodes.clear()
        tex=nodes.new('ShaderNodeTexImage');tex.image=image;tex.interpolation='Closest';tex.extension='CLIP'
        bsdf=nodes.new('ShaderNodeBsdfPrincipled');bsdf.inputs['Roughness'].default_value=.85
        output=nodes.new('ShaderNodeOutputMaterial')
        mat.node_tree.links.new(tex.outputs['Color'],bsdf.inputs['Base Color']);mat.node_tree.links.new(tex.outputs['Alpha'],bsdf.inputs['Alpha'])
        mat.node_tree.links.new(bsdf.outputs['BSDF'],output.inputs['Surface']);materials[ref]=mat;return mat
    root=bpy.data.objects.new('MODEL_ROOT',None);scene.collection.objects.link(root);objects=[]
    for index,e in enumerate(data['elements']):
        x0,y0,z0=e['from'];x1,y1,z1=e['to']
        sides={
          'north':[(x1,y0,z0),(x0,y0,z0),(x0,y1,z0),(x1,y1,z0)],
          'south':[(x0,y0,z1),(x1,y0,z1),(x1,y1,z1),(x0,y1,z1)],
          'east':[(x1,y0,z1),(x1,y0,z0),(x1,y1,z0),(x1,y1,z1)],
          'west':[(x0,y0,z0),(x0,y0,z1),(x0,y1,z1),(x0,y1,z0)],
          'up':[(x0,y1,z1),(x1,y1,z1),(x1,y1,z0),(x0,y1,z0)],
          'down':[(x0,y0,z0),(x1,y0,z0),(x1,y0,z1),(x0,y0,z1)]}
        vertices=[];polygons=[];uvs=[];mats=[];rotation=e.get('rotation')
        for face,spec in e['faces'].items():
            coords=sides[face]
            if rotation:
                origin=Vector(rotation['origin']);axis=rotation['axis'];angle=math.radians(rotation['angle'])
                matrix=Matrix.Rotation(angle,3,axis.upper());transformed=[]
                for p in coords:
                    v=matrix@(Vector(p)-origin)
                    if rotation.get('rescale',False):
                        for k in range(3):
                            if k!='xyz'.index(axis):v[k]/=math.cos(angle)
                    transformed.append(v+origin)
                coords=transformed
            start=len(vertices);vertices.extend(convert(p) for p in coords);polygons.append(tuple(range(start,start+4)))
            if 'uv' not in spec:raise ValueError(f'{model_id}/{e.get("name",index)}/{face}: explicit UV required')
            u0,v0,u1,v1=spec['uv']
            uv=[(u0/16,1-v1/16),(u1/16,1-v1/16),(u1/16,1-v0/16),(u0/16,1-v0/16)]
            steps=spec.get('rotation',0)//90;uv=uv[steps:]+uv[:steps];uvs.extend(uv);mats.append(material(spec['texture']))
        mesh=bpy.data.meshes.new(e.get('name',str(index)));mesh.from_pydata(vertices,[],polygons);mesh.update()
        obj=bpy.data.objects.new(e.get('name',str(index)),mesh);scene.collection.objects.link(obj);obj.parent=root;objects.append(obj)
        layer=mesh.uv_layers.new(name='Minecraft face UV')
        for i,uv in enumerate(uvs):layer.data[i].uv=uv
        for i,mat in enumerate(mats):mesh.materials.append(mat);mesh.polygons[i].material_index=i
        obj['minecraft_element']=json.dumps(e);obj['review_model_part']=True
    for name,pos,energy,size in [('Key',(3,4,6),450,5),('Fill',(-4,2,3),180,4),('Rim',(1,-4,4),240,3)]:
        light=bpy.data.lights.new(name,'AREA');light.energy=energy;light.shape='DISK';light.size=size
        obj=bpy.data.objects.new(name,light);scene.collection.objects.link(obj);obj['review_direction']=pos
    camera=bpy.data.objects.new('Review camera',bpy.data.cameras.new('Review camera'));scene.collection.objects.link(camera);scene.camera=camera
    lo,hi=frame(scene,objects)
    scene['model_id']=model_id;scene['bounds_low']=list(lo);scene['bounds_high']=list(hi)
    scene['review_notice']=NOTICE;scene['source_hashes']=json.dumps(hashes);scene['display_json']=json.dumps(data['display'])
    scene['prepared_pid']=os.getpid();scene['importer_version']=3
    blend=PROD/'sources/blend'/f'{model_id}.blend';blend.parent.mkdir(parents=True,exist_ok=True)
    # Camera, lights and packed textures are all present in the saved editable source.
    bpy.ops.wm.save_as_mainfile(filepath=str(blend),compress=False)
    print(json.dumps({'phase':'prepared','id':model_id,'elements':len(objects),'blend':str(blend)}),flush=True)

def plain_material(name,color):
    mat=bpy.data.materials.new(name);mat.diffuse_color=(*color,1);mat.use_nodes=True
    bsdf=next(n for n in mat.node_tree.nodes if n.type=='BSDF_PRINCIPLED')
    bsdf.inputs['Base Color'].default_value=(*color,1);bsdf.inputs['Roughness'].default_value=.8
    return mat

def jig_box(name,low,high,material):
    low,high=Vector(low),Vector(high)
    # Accept converted native endpoints, whose Z->-Y remapping reverses endpoint order.
    lo=Vector(tuple(min(low[i],high[i]) for i in range(3)));hi=Vector(tuple(max(low[i],high[i]) for i in range(3)))
    bpy.ops.mesh.primitive_cube_add(size=1,location=(lo+hi)/2)
    obj=bpy.context.object;obj.name=name;obj.dimensions=hi-lo;obj.data.materials.append(material);obj['review_jig']=True
    bpy.context.view_layer.update()
    return obj

def display_matrix(spec,left=False):
    # Official 1.21.4 ItemTransform.apply (gnp, inspected using Mojang mappings + javap):
    # left hand negates translation.x and rotation.y/z; translate -> rotationXYZ -> scale.
    angles=list(spec.get('rotation',[0,0,0]));translation=list(spec.get('translation',[0,0,0]))
    if left:angles[1]*=-1;angles[2]*=-1;translation[0]*=-1
    rx,ry,rz=(Matrix.Rotation(math.radians(a),4,axis) for a,axis in zip(angles,'XYZ'))
    scale=Matrix.Diagonal((*spec.get('scale',[1,1,1]),1))
    return AXIS4@Matrix.Translation(Vector(translation)/16)@rx@ry@rz@scale@AXIS4.inverted()

def render_png(scene,path,note):
    path.parent.mkdir(parents=True,exist_ok=True);scene.render.use_stamp=True;scene.render.use_stamp_note=True
    scene.render.stamp_note_text=note;scene.render.stamp_font_size=10
    for flag in ['date','time','render_time','frame','frame_range','memory','hostname','camera','lens','scene','marker','filename']:
        attr='use_stamp_'+flag
        if hasattr(scene.render,attr):setattr(scene.render,attr,False)
    scene.render.filepath=str(path);bpy.ops.render.render(write_still=True)
    if not path.is_file() or path.stat().st_size==0:raise RuntimeError('Missing PNG: '+str(path))

def context_views(scene,model_id,objects,data):
    root=scene.objects['MODEL_ROOT'];dest=PROD/'review/contexts'/model_id;records=[]
    anchor_path=PROD/'sources/class-weapons-anchors.json'
    anchors=json.loads(anchor_path.read_text(encoding='utf-8')).get('models',{}) if anchor_path.exists() else {}
    scene.render.resolution_x=scene.render.resolution_y=480
    neutral=plain_material('JIG slate',(.24,.31,.39));glove=plain_material('JIG glove',(.62,.40,.28))
    marker=plain_material('JIG anchor cyan',(.15,.82,.77))
    def clear_jigs():
        for obj in list(scene.objects):
            if obj.get('review_jig'):bpy.data.objects.remove(obj,do_unlink=True)
        root.matrix_world=Matrix.Identity(4);bpy.context.view_layer.update()
    if model_id.startswith(('prop_','furn_')):
        for degrees in ([0,45] if model_id.startswith('furn_') else [0]):
            clear_jigs();root.matrix_world=Matrix.Rotation(math.radians(degrees),4,'Z');bpy.context.view_layer.update();lo,hi=bounds(objects)
            floor=jig_box('JIG Y=0 floor',(lo.x-.15,lo.y-.15,-.55),(hi.x+.15,hi.y+.15,-.5),neutral)
            frame(scene,objects+[floor]);name='floor_%02d'%degrees
            render_png(scene,dest/f'context-{name}.png',f'{model_id} / FLOOR Y=0 / yaw {degrees}')
            gap=(lo.z+.5)*16
            records.append({'context':name,'yawDegrees':degrees,'lowestYModelUnits':round(gap,6),'touchesFloor':abs(gap)<.001,'penetratesFloor':gap<-.001,'notice':'ItemDisplay NONE reference, no server entity transformation.'})
    elif model_id.startswith('hat_'):
        clear_jigs();actual=display_matrix(data['display']['head'])
        baseline=display_matrix({'rotation':[0,180,0],'scale':[.625,.625,.625]})
        root.matrix_world=Matrix.Scale(.625,4)@actual
        head=jig_box('JIG 8px head',convert((1.6,1.6,1.6)),convert((14.4,14.4,14.4)),glove)
        head.matrix_world=baseline@head.matrix_world;marks=[]
        for x in [4.1,10.2]:
            eye=jig_box('JIG eye',convert((x,8.1,1.55)),convert((x+1.7,9.8,1.35)),neutral)
            eye.matrix_world=baseline@eye.matrix_world;marks.append(eye)
        frame(scene,objects+[head]+marks,(-4,-6,3.4))
        render_png(scene,dest/'context-head.png',model_id+' / APPROXIMATE HEAD JIG')
        records.append({'context':'head','display':data['display']['head'],'reference':'V2 headband frame: 12.8-unit head cube, common 0.625 scale','approximation':True,'requiresClientVerification':True})
    elif model_id in anchors:
        anchor=anchors[model_id]
        for context in HANDS:
            clear_jigs();spec=data['display'][context];matrix=display_matrix(spec,context.endswith('lefthand'));root.matrix_world=matrix
            target_native=Vector(anchor['contexts'][context]['target']);target=AXIS@(target_native/16)
            actual=matrix@convert(anchor['anchor'])
            palm=jig_box('JIG palm',target+Vector((-.13,-.07,-.13)),target+Vector((.13,.08,.11)),glove)
            fingers=[jig_box('JIG finger '+str(i),target+Vector((.06,-.09,-.11+i*.065)),target+Vector((.16,-.015,-.06+i*.065)),glove) for i in range(3)]
            cuff=jig_box('JIG wrist cuff',target+Vector((-.10,-.04,-.32)),target+Vector((.10,.12,-.13)),neutral)
            mark=jig_box('JIG target',target+Vector((-.035,-.095,-.035)),target+Vector((.035,-.085,.035)),marker)
            frame(scene,objects+[palm,cuff,mark]+fingers,(4,6,3) if context.endswith('righthand') else (-4,6,3))
            render_png(scene,dest/f'context-{context}.png',model_id+' / '+context+' / APPROXIMATE')
            forward=(matrix.to_3x3()@(AXIS@Vector(anchor['forward']))).normalized()
            record={'context':context,'display':spec,'kind':anchor['kind'],'leftHandMirroringApplied':context.endswith('lefthand'),'anchorModelUnits':anchor['anchor'],'targetDisplayUnits':list(target_native),'actualAnchorDisplayUnits':[round(v,6) for v in (AXIS.inverted()@actual)*16],'anchorErrorModelUnits':round((actual-target).length*16,6),'forwardBlender':[round(v,6) for v in forward],'approximation':True,'requiresClientVerification':True}
            if 'stringSide' in anchor:record['bowStringSideBlender']=[round(v,6) for v in (matrix.to_3x3()@(AXIS@Vector(anchor['stringSide']))).normalized()]
            records.append(record)
    clear_jigs();scene.render.resolution_x=scene.render.resolution_y=640
    write_json(dest/'contexts.json',{'id':model_id,'notice':'APPROXIMATE JIG; Minecraft client verification required.','matrixConvention':'ItemTransform T @ Rx @ Ry @ Rz @ S around [8,8,8]; actual left-hand mirroring applied. Enclosing player matrices and animated poses not reproduced.','contexts':records})
    return records

def render_saved(model_id,device,with_contexts):
    blend=PROD/'sources/blend'/f'{model_id}.blend';bpy.ops.wm.open_mainfile(filepath=str(blend));scene=bpy.context.scene
    if scene.get('model_id')!=model_id:raise RuntimeError('Saved blend ID mismatch')
    if scene.get('prepared_pid')==os.getpid():raise RuntimeError('Use a fresh Blender process for --render-saved.')
    hashes=json.loads(scene['source_hashes'])
    for filename,expected in hashes.items():
        if sha(Path(filename))!=expected:raise RuntimeError('Source changed after preparation: '+filename)
    for img in bpy.data.images:
        if img.source=='FILE' and not img.packed_file:raise RuntimeError('Texture not packed: '+img.name)
    selection=configure_cycles(scene,device);objects=[o for o in scene.objects if o.get('review_model_part')]
    if not objects:raise RuntimeError('No editable model parts')
    dest=PROD/'review/models'/model_id
    for name,direction in VIEWS.items():
        frame(scene,objects,direction);render_png(scene,dest/f'{name}.png',model_id+' / '+name+' / BLENDER GEOMETRY REVIEW')
    lo,hi=bounds(objects);data=json.loads(model_path(model_id).read_text(encoding='utf-8'))
    records=context_views(scene,model_id,objects,data) if with_contexts else []
    write_json(dest/'geometry.json',{'id':model_id,'elements':len(objects),'bounds_model_units':[[round(v*16+8,4) for v in [lo.x,lo.z,-hi.y]],[round(v*16+8,4) for v in [hi.x,hi.z,-lo.y]]],'source':str(blend),'views':list(VIEWS),'engine':'Blender Cycles 20 samples / Standard / nearest texture','exposure':scene.view_settings.exposure,'exposureNote':'Chef review exposure reduced from -0.7 to -1.7 after initial highlight-clipping review; asset texture colors unchanged. Compare before/after geometry with this lighting difference in mind.' if model_id=='hat_chef_toque' else 'Baseline review exposure -0.7.','device':selection,'freshProcessReopen':True,'packedTextures':True,'sourceSha256':hashes,'contextImages':[r['context'] for r in records],'notice':NOTICE,'limitations':['Face UV rotation not fixture-certified against Minecraft.','Cycles alpha/lighting and ignored shade/cullface/gui_light differ from vanilla.','Context jigs do not certify enclosing player/entity transforms, FOV, attack or bow animation.']})
    print(json.dumps({'phase':'rendered','id':model_id,'views':4,'contexts':len(records),'device':selection['device']}),flush=True)

def main():
    parser=argparse.ArgumentParser(description=__doc__);mode=parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--prepare',action='store_true');mode.add_argument('--render-saved',action='store_true')
    parser.add_argument('--device',choices=['AUTO','CPU','GPU'],default='CPU');parser.add_argument('--no-contexts',action='store_true');parser.add_argument('ids',nargs='+')
    options=parser.parse_args(sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else [])
    for model_id in options.ids:
        model_path(model_id)
        if options.prepare:create_model(model_id,options.device)
        else:render_saved(model_id,options.device,not options.no_contexts)
if __name__=='__main__':main()
