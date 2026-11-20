"""Assemble rigid ItemDisplay bones from the delivered manifest, never hand-place them."""
import argparse, importlib.util, json, math, os, sys
from pathlib import Path
import bpy
from mathutils import Matrix, Vector

spec=importlib.util.spec_from_file_location('model_review',Path(__file__).with_name('render-models.py'))
base=importlib.util.module_from_spec(spec);spec.loader.exec_module(base)
MANIFEST=base.OUT/'monster_rigs.json'
POSES={'neutral':0,'windup-minus60':-60,'strike-plus55':55}

def manifest():
    data=json.loads(MANIFEST.read_text(encoding='utf-8'))
    assert data['unitsPerBlock']==16 and data['posePitchToLocalX']==-1
    assert data['modelOrigin']==[8,8,8] and data['rootAnchor']==[8,0,8]
    return data

def set_pose(scene,pitch=0,head_yaw=0):
    for obj in scene.objects:
        role=obj.get('rig_role')
        if role in ['arm','weapon']:obj.rotation_euler=(math.radians(-pitch),0,0)
        elif role=='head':obj.rotation_euler=(0,0,math.radians(head_yaw))
    bpy.context.view_layer.update()

def prepare(rig_id,device):
    data=manifest();rig=data['rigs'][rig_id];scene=base.initialize_scene(device)
    root=bpy.data.objects.new('ASSEMBLED_RIG',None);scene.collection.objects.link(root)
    root.scale=(rig['baseScale'],)*3;root['rig_id']=rig_id
    hashes={str(MANIFEST):base.sha(MANIFEST)};all_objects=[]
    parts={'root':{'model':rig['rootModel'],'offset':[0,8,0]},**rig['sockets']}
    for role,part in parts.items():
        model_id=part['model'].split(':',1)[1]
        objects,source_hashes,model,bone=base.create_model(model_id,device,append_to=root)
        bone.location=base.AXIS@(Vector(part['offset'])/16)
        bone['rig_role']=role;bone['item_model']=part['model'];bone['socket_offset']=part['offset']
        for obj in objects:obj['rig_role_mesh']=role
        hashes.update(source_hashes);all_objects.extend(objects)
    set_pose(scene)
    for role in ['arm','weapon']:
        bone=next((o for o in scene.objects if o.get('rig_role')==role),None)
        if bone:
            for frame,pitch in [(1,0),(20,-60),(28,55),(40,0)]:
                bone.rotation_euler=(math.radians(-pitch),0,0);bone.keyframe_insert(data_path='rotation_euler',frame=frame)
    scene.frame_start=1;scene.frame_end=40;scene.frame_set(1);set_pose(scene)
    base.scale_review_lights(scene,rig['baseScale'])
    base.frame(scene,all_objects)
    scene['rig_id']=rig_id;scene['source_hashes']=json.dumps(hashes);scene['rig_contract']=json.dumps(rig)
    scene['prepared_pid']=os.getpid();scene['review_notice']=base.NOTICE
    blend=base.PROD/'sources/blend/rigs'/f'{rig_id}.blend';blend.parent.mkdir(parents=True,exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=str(blend),compress=False)
    print(json.dumps({'phase':'prepared-rig','id':rig_id,'bones':len(parts),'elements':len(all_objects)}),flush=True)

def numeric_checks(scene,rig):
    root=scene.objects['ASSEMBLED_RIG'];original_scale=root.scale.copy();checks=[]
    # This independent golden point catches the commonly reversed Bukkit-yaw/pitch signs.
    point=Matrix.Rotation(math.pi,4,'Z') @ (Vector((0,0,1))+Matrix.Rotation(math.radians(60),4,'X')@Vector((0,1,0)))
    expected=Vector((0,-.5,1.8660254037844386))
    assert (point-expected).length<1e-7,'Golden fixture failed'
    maximum_error=0;maximum_endpoint_error=0
    for scale in [1,2,rig['baseScale']]:
        root.scale=(scale,)*3
        for yaw in [0,90,180,270]:
            root.rotation_euler=(0,0,math.radians(180-yaw))
            for pitch in [-70,-65,-60,0,55]:
                set_pose(scene,pitch)
                for role,part in rig['sockets'].items():
                    bone=next(o for o in scene.objects if o.get('rig_role')==role)
                    actual=bone.matrix_world@Vector((0,0,0))
                    expected=Matrix.Rotation(math.radians(180-yaw),4,'Z')@(base.AXIS@Vector(part['offset'])*scale/16)
                    error=(actual-expected).length;maximum_error=max(maximum_error,error)
                    assert error<=.01,f'{role}: socket drift {error}'
                    endpoint=bone.matrix_world@Vector((0,1,0))
                    pitch_matrix=Matrix.Rotation(math.radians(-pitch if role in ['arm','weapon'] else 0),4,'X')
                    expected_endpoint=expected+Matrix.Rotation(math.radians(180-yaw),4,'Z')@(pitch_matrix@Vector((0,scale,0)))
                    endpoint_error=(endpoint-expected_endpoint).length;maximum_endpoint_error=max(maximum_endpoint_error,endpoint_error)
                    assert endpoint_error<=.00001,f'{role}: posed endpoint drift {endpoint_error}'
                checks.append({'scale':scale,'yaw':yaw,'semanticPitch':pitch})
    root.scale=original_scale;root.rotation_euler=(0,0,0);set_pose(scene)
    return {'goldenFixture':{'bukkitYaw':0,'pitch':-60,'nativeExpectedPoint':[0,1.8660254037844386,.5],'pass':True},'cases':len(checks),'maximumSocketErrorBlocks':maximum_error,'maximumPosedEndpointErrorBlocks':maximum_endpoint_error,'yawValues':[0,90,180,270],'pitchValues':[-70,-65,-60,0,55],'scaleValues':[1,2,rig['baseScale']]}

def fixed_frame(scene,lo,hi,direction):
    center=(lo+hi)/2;span=max(hi-lo);camera=scene.camera
    camera.data.type='ORTHO';camera.data.ortho_scale=span*1.4
    camera.location=center+Vector(direction)*max(span,1)
    camera.rotation_euler=(center-camera.location).to_track_quat('-Z','Y').to_euler()
    for o in scene.objects:
        if o.type=='LIGHT' and 'review_direction' in o:
            o.location=center+Vector(o['review_direction'])*max(span,.8)/2
            o.rotation_euler=(center-o.location).to_track_quat('-Z','Y').to_euler()

def render_saved(rig_id,device):
    blend=base.PROD/'sources/blend/rigs'/f'{rig_id}.blend';bpy.ops.wm.open_mainfile(filepath=str(blend));scene=bpy.context.scene
    assert scene['rig_id']==rig_id and scene['prepared_pid']!=os.getpid(),'Fresh saved-scene reopen required'
    hashes=json.loads(scene['source_hashes']);rig=manifest()['rigs'][rig_id]
    for filename,expected in hashes.items():assert base.sha(Path(filename))==expected,'Stale rig source: '+filename
    for image in bpy.data.images:
        if image.source=='FILE':assert image.packed_file,'Unpacked texture '+image.name
    # Preserve editable animation in the .blend, but disable action evaluation
    # in the reopened review process so frame 1 cannot restore a neutral pose.
    for obj in scene.objects:
        if obj.get('rig_role') and obj.animation_data:obj.animation_data_clear()
    selection=base.configure_cycles(scene,device);objects=[o for o in scene.objects if o.get('review_model_part')]
    math_proof=numeric_checks(scene,rig);pose_bounds={}
    for name,pitch in POSES.items():
        set_pose(scene,pitch);lo,hi=base.bounds(objects);pose_bounds[name]=(lo.copy(),hi.copy())
    set_pose(scene)
    root_objects=[o for o in objects if o.get('rig_role_mesh')=='root']
    root_lo,root_hi=base.bounds(root_objects);assert abs(root_lo.z)<.0001,'Root not on floor'
    low=Vector(tuple(min(b[0][i] for b in pose_bounds.values()) for i in range(3)))
    high=Vector(tuple(max(b[1][i] for b in pose_bounds.values()) for i in range(3)))
    mat=base.plain_material('JIG neutral floor',(.12,.16,.21))
    floor=base.jig_box('JIG floor contact',(low.x-.15,low.y-.15,-.045),(high.x+.15,high.y+.15,0),mat)
    dest=base.PROD/'review/rigs'/rig_id;views=[];evaluated_poses=[]
    schedule=[('front',0,base.VIEWS['front']),('side',0,base.VIEWS['side']),('rear',0,base.VIEWS['rear']),('threequarter',0,base.VIEWS['threequarter']),('windup-minus60',-60,base.VIEWS['threequarter']),('windup-minus60-side',-60,base.VIEWS['side']),('strike-plus55',55,base.VIEWS['threequarter'])]
    for name,pitch,direction in schedule:
        set_pose(scene,pitch);fixed_frame(scene,low,high,direction)
        base.render_png(scene,dest/f'{name}.png',f'{rig_id} / {name} / scale {rig["baseScale"]} / ASSEMBLED ITEMDISPLAY BONES')
        action_bone=next(o for o in scene.objects if o.get('rig_role') in ['arm','weapon'])
        actual_angle=math.degrees(action_bone.rotation_euler.x)
        assert abs(actual_angle+pitch)<.001,'Animation overrode review pose'
        evaluated_poses.append({'view':name,'semanticPitch':pitch,'actualLocalXDegrees':actual_angle,'forwardWorld':list(action_bone.matrix_world.to_3x3()@Vector((0,1,0)))})
        views.append(name)
    # Head look-at rotations share the exact saved neck pivot.
    head_checks=[]
    if 'head' in rig['sockets']:
        head=next(o for o in scene.objects if o.get('rig_role')=='head');pivot=head.matrix_world.translation.copy()
        for degrees in [-45,45]:
            set_pose(scene,0,degrees);error=(head.matrix_world.translation-pivot).length;assert error<.01
            head_checks.append({'yaw':degrees,'pivotErrorBlocks':error})
    set_pose(scene)
    base.write_json(dest/'assembly.json',{'id':rig_id,'status':'PASS','baseScale':rig['baseScale'],'parts':{'root':rig['rootModel'],**{k:v['model'] for k,v in rig['sockets'].items()}},'socketManifest':str(MANIFEST),'source':str(blend),'blendSha256':base.sha(blend),'sourceSha256':hashes,'freshProcessReopen':True,'preparedPid':scene['prepared_pid'],'renderPid':os.getpid(),'packedTextures':True,'device':selection,'views':views,'elements':len(objects),'rootMinimumYBlocks':root_lo.z,'math':math_proof,'headChecks':head_checks,'evaluatedPoses':evaluated_poses,'poses':{name:{'semanticPitch':POSES[name],'boundsBlenderBlocks':[list(lo),list(hi)]} for name,(lo,hi) in pose_bounds.items()},'camera':'All poses share union bounds, identical lights, exposure -0.7 and 640px resolution.','notice':base.NOTICE,'limitations':['Rigid ItemDisplay parts, not a deforming skinned mesh.','Runtime interpolation, AI, hitboxes and Minecraft lighting need SOL integration checks.','Geometric socket tests do not certify silhouette or absence of interpenetration; inspect rendered poses.']})
    print(json.dumps({'phase':'rendered-rig','id':rig_id,'views':len(views),'elements':len(objects)}),flush=True)

def main():
    parser=argparse.ArgumentParser(description=__doc__);mode=parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--prepare',action='store_true');mode.add_argument('--render-saved',action='store_true')
    parser.add_argument('--device',choices=['AUTO','CPU','GPU'],default='CPU');parser.add_argument('--no-contexts',action='store_true');parser.add_argument('ids',nargs='+')
    args=parser.parse_args(sys.argv[sys.argv.index('--')+1:])
    for rig_id in args.ids:
        assert rig_id in manifest()['rigs'],'Unknown rig'
        if args.prepare:prepare(rig_id,args.device)
        else:render_saved(rig_id,args.device)
if __name__=='__main__':main()
