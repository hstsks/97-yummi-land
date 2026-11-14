"""Reimport delivered Minecraft JSON/PNG, save an editable blend, reopen and render.
Blender-only command: blender -b --factory-startup --python-exit-code 1 --python this.py -- [model IDs]
Review approximation: vanilla face lighting, UI/entity transforms and resource loading still require Minecraft.
"""
import bpy, json, math, sys
from pathlib import Path
from mathutils import Vector, Matrix

ROOT=Path(__file__).resolve().parents[3]
OUT=ROOT/'resourcepack-gen/overrides/assets/yuumi97'
PROD=ROOT/'assets/yuumi97-production'

def convert(p): return Vector(((p[0]-8)/16, -(p[2]-8)/16, (p[1]-8)/16))

def create_model(model_id):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    data=json.loads((OUT/'models/item'/f'{model_id}.json').read_text(encoding='utf-8'))
    scene=bpy.context.scene
    scene.render.engine='CYCLES'
    scene.cycles.samples=20
    scene.cycles.use_denoising=True
    scene.render.resolution_x=640; scene.render.resolution_y=640; scene.render.resolution_percentage=100
    scene.render.image_settings.file_format='PNG'
    scene.render.film_transparent=False
    scene.view_settings.view_transform='Standard'
    scene.view_settings.look='None'
    scene.view_settings.exposure=-0.7
    world=bpy.data.worlds.new('Neutral studio'); world.use_nodes=True
    world.node_tree.nodes.get('Background').inputs[0].default_value=(.17,.19,.23,1)
    world.node_tree.nodes.get('Background').inputs[1].default_value=.45
    scene.world=world
    materials={}
    def material(ref):
        while ref.startswith('#'): ref=data['textures'][ref[1:]]
        if ref in materials:return materials[ref]
        namespace,local=ref.split(':')
        png=ROOT/'resourcepack-gen/overrides/assets'/namespace/'textures'/f'{local}.png'
        image=bpy.data.images.load(str(png));image.pack()
        mat=bpy.data.materials.new(ref); mat.use_nodes=True
        nodes=mat.node_tree.nodes;nodes.clear()
        tex=nodes.new('ShaderNodeTexImage');tex.image=image;tex.interpolation='Closest';tex.extension='CLIP'
        bsdf=nodes.new('ShaderNodeBsdfPrincipled');bsdf.inputs['Roughness'].default_value=.85
        output=nodes.new('ShaderNodeOutputMaterial')
        mat.node_tree.links.new(tex.outputs['Color'],bsdf.inputs['Base Color'])
        mat.node_tree.links.new(tex.outputs['Alpha'],bsdf.inputs['Alpha'])
        mat.node_tree.links.new(bsdf.outputs['BSDF'],output.inputs['Surface'])
        materials[ref]=mat;return mat
    objects=[]
    for index,e in enumerate(data['elements']):
        x0,y0,z0=e['from'];x1,y1,z1=e['to']
        sides={
          'north':[(x1,y0,z0),(x0,y0,z0),(x0,y1,z0),(x1,y1,z0)],
          'south':[(x0,y0,z1),(x1,y0,z1),(x1,y1,z1),(x0,y1,z1)],
          'east':[(x1,y0,z1),(x1,y0,z0),(x1,y1,z0),(x1,y1,z1)],
          'west':[(x0,y0,z0),(x0,y0,z1),(x0,y1,z1),(x0,y1,z0)],
          'up':[(x0,y1,z1),(x1,y1,z1),(x1,y1,z0),(x0,y1,z0)],
          'down':[(x0,y0,z0),(x1,y0,z0),(x1,y0,z1),(x0,y0,z1)]}
        vertices=[];polygons=[];uvs=[];mats=[]
        rotation=e.get('rotation')
        for face,spec in e['faces'].items():
            coords=sides[face]
            if rotation:
                origin=Vector(rotation['origin']);axis=rotation['axis'];angle=math.radians(rotation['angle'])
                matrix=Matrix.Rotation(angle,3,axis.upper());rescale=rotation.get('rescale',False)
                rotated=[]
                for p in coords:
                    v=matrix@(Vector(p)-origin)
                    if rescale:
                        for k in range(3):
                            if k!='xyz'.index(axis):v[k]/=math.cos(angle)
                    rotated.append(v+origin)
                coords=rotated
            start=len(vertices);vertices.extend([convert(p) for p in coords]);polygons.append(tuple(range(start,start+4)))
            u0,v0,u1,v1=spec.get('uv',[0,0,16,16]); uv=[(u0/16,1-v1/16),(u1/16,1-v1/16),(u1/16,1-v0/16),(u0/16,1-v0/16)]
            steps=spec.get('rotation',0)//90;uv=uv[steps:]+uv[:steps]
            uvs.extend(uv);mats.append(material(spec['texture']))
        mesh=bpy.data.meshes.new(e.get('name',str(index)));mesh.from_pydata(vertices,[],polygons);mesh.update()
        obj=bpy.data.objects.new(e.get('name',str(index)),mesh);scene.collection.objects.link(obj);objects.append(obj)
        layer=mesh.uv_layers.new(name='Minecraft face UV')
        for i,uv in enumerate(uvs):layer.data[i].uv=uv
        for i,mat in enumerate(mats):mesh.materials.append(mat);mesh.polygons[i].material_index=i
        obj['minecraft_element']=json.dumps(e)
    corners=[v.co for o in objects for v in o.data.vertices]
    low=Vector(tuple(min(p[i] for p in corners) for i in range(3)));high=Vector(tuple(max(p[i] for p in corners) for i in range(3)))
    center=(low+high)/2;span=max(high-low)
    scene['model_id']=model_id;scene['bounds_low']=list(low);scene['bounds_high']=list(high)
    scene['review_notice']='JSON/PNG geometry review; Minecraft engine verification required separately.'
    for name,pos,energy,size in [('Key',(3,4,6),450,5),('Fill',(-4,2,3),180,4),('Rim',(1,-4,4),240,3)]:
        light=bpy.data.lights.new(name,'AREA');light.energy=energy;light.shape='DISK';light.size=size
        obj=bpy.data.objects.new(name,light);scene.collection.objects.link(obj);obj.location=center+Vector(pos)*max(span,.8)/2
        obj.rotation_euler=(center-obj.location).to_track_quat('-Z','Y').to_euler()
    camera=bpy.data.objects.new('Review camera',bpy.data.cameras.new('Review camera'));scene.collection.objects.link(camera)
    camera.data.type='ORTHO';camera.data.ortho_scale=span*1.5;scene.camera=camera
    blend=PROD/'sources/blend'/f'{model_id}.blend';blend.parent.mkdir(parents=True,exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=str(blend))
    bpy.ops.wm.open_mainfile(filepath=str(blend))
    scene=bpy.context.scene;camera=scene.camera
    dest=PROD/'review/models'/model_id;dest.mkdir(parents=True,exist_ok=True)
    views={'front':(0,6,.4),'side':(6,0,.4),'rear':(0,-6,.4),'threequarter':(4,6,3.4)}
    for name,direction in views.items():
        camera.location=center+Vector(direction)*max(span,1)
        camera.rotation_euler=(center-camera.location).to_track_quat('-Z','Y').to_euler()
        scene.render.filepath=str(dest/f'{name}.png');bpy.ops.render.render(write_still=True)
    (dest/'geometry.json').write_text(json.dumps({'id':model_id,'elements':len(objects),'bounds_model_units':[[round(v*16+8,4) for v in [low[0],low[2],-high[1]]],[round(v*16+8,4) for v in [high[0],high[2],-low[1]]]],'source':str(blend),'views':list(views),'engine':'Blender Cycles 20 samples / Standard / nearest texture'},indent=2))

ids=sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else []
if not ids:ids=[p.stem for p in sorted((OUT/'models/item').glob('*.json'))]
for model_id in ids:create_model(model_id)
