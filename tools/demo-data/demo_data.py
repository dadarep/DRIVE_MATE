"""Reversible, explicit sample data for the installed DRIVE_MATE debug emulator app."""
import argparse,copy,datetime,json,os,subprocess,time,urllib.request
from pathlib import Path
import xml.etree.ElementTree as ET

ADB=Path(os.environ['LOCALAPPDATA'])/'Android/Sdk/platform-tools/adb.exe'
BACKUPS=Path.home()/'Downloads/DRIVE_MATE_demo_data_backups'
MANIFEST=BACKUPS/'active-demo.json'
BASE='http://127.0.0.1:18088'
PREFIX='demo-plan-20261112/'

def adb(*args,data=None):
    r=subprocess.run([str(ADB),'-s','emulator-5554',*args],input=data,capture_output=True,timeout=15,creationflags=subprocess.CREATE_NO_WINDOW)
    if r.returncode:raise RuntimeError(r.stderr.decode('utf-8',errors='replace'))
    return r.stdout
def api(path,method='GET'):
    with urllib.request.urlopen(urllib.request.Request(BASE+path,method=method),timeout=5) as r:return json.load(r)
def value(root,key,default=None):
    e=next((e for e in root if e.get('name')==key),None)
    return default if e is None else e.text if e.tag=='string' else e.get('value')
def remove(root,*keys):
    for e in list(root):
        if e.get('name') in keys:root.remove(e)
def put(root,key,val):
    remove(root,key)
    if isinstance(val,bool):e=ET.SubElement(root,'boolean',name=key,value=str(val).lower())
    elif isinstance(val,int):e=ET.SubElement(root,'int',name=key,value=str(val))
    else:e=ET.SubElement(root,'string',name=key);e.text=val
    return e
def compact(obj):return json.dumps(obj,ensure_ascii=False,separators=(',',':'))

def seeded(base,profile,now_ms):
    root=copy.deepcopy(base)
    garden=json.loads(value(root,'garden','{"reflections":[],"legacyLeaves":0}'))
    # Baseline is the original snapshot; stable IDs also keep accidental reapplication idempotent.
    history=[r for r in garden.get('reflections',[]) if not r['goalId'].startswith(PREFIX)]
    for i,(goal,response,contexts) in enumerate([
        ('시연 · 출발할 때 천천히 가속하기','GOOD',['FLOW']),
        ('시연 · 정지선 앞에서 여유 있게 멈추기','DIFFICULT',['CUT_IN'])],1):
        history.append(dict(goalId=PREFIX+f'history-goal-{i}',goalText=goal,afterSessionId=PREFIX+f'history-trip-{i}',
                            response=response,contexts=contexts,completedAt=now_ms-(3-i)*86400000))
    sessions=int(value(root,'sessions','0'))+3
    garden['reflections']=history
    garden['goal']=dict(id=PREFIX+'active-goal',text='시연 · 신호가 바뀌기 전에 미리 속도 줄이기',
                        baseSessionId=PREFIX+'history-trip-2',baseSession=sessions if profile=='departure' else sessions-1)
    put(root,'garden',compact(garden));put(root,'sessions',sessions)
    put(root,'recording',False);put(root,'reviewed',profile=='departure')
    put(root,'lastSessionId',PREFIX+'arrival-trip');put(root,'lastCount',2)
    put(root,'lastMoments',compact([dict(t=98000,**{'from':48,'to':18}),dict(t=215000,**{'from':35,'to':10})]))
    put(root,'lastContexts','{}');put(root,'lastGapMs',0).tag='long'
    put(root,'lastInsufficient',False);put(root,'lastInterrupted',False)
    put(root,'lastReminder','다음 운전엔 앞 상황을 미리 살피고, 여유 있게 속도를 줄여 주세요.')
    put(root,'lastRoute',compact([[37.50122,127.03864],[37.5008,127.0372],[37.4999,127.0343],[37.49795,127.02762]]))
    if profile=='departure':
        put(root,'destinationName','시연 · 42dot');put(root,'destinationLat','37.48544722');put(root,'destinationLon','127.03636666')
    elif value(root,'destinationLat') is None:
        put(root,'destinationName','강남역');put(root,'destinationLat','37.49795');put(root,'destinationLon','127.02762')
    remove(root,'pendingCount','pendingSamples','pendingGapMs','sessionStart','pendingMoments','pendingRoute',
           'pendingSessionId','pendingSeatbeltWarning','pendingRapidAcceleration','goalDraft')
    return root

def park():
    adb('forward','tcp:18088','tcp:8088')
    api('/browser/session?client=drivemate-seed','POST')
    api('/vehicle/controls?speed=0&accelerator=0&brake=0&steering=0','PUT')
    api('/vehicle/gear?gear=Park','PUT')
    deadline=time.monotonic()+12
    while time.monotonic()<deadline:
        api('/browser/session?client=drivemate-seed','POST')
        root=ET.fromstring(adb('exec-out','run-as','com.drivemate.app','--user','10','cat','shared_prefs/driving.xml'))
        if value(root,'gearLabel')=='P' and value(root,'vehicleState')=='parked' and value(root,'recording','false')=='false':return
        time.sleep(.5)
    raise RuntimeError('Park was not confirmed; no app data was changed')

def write_prefs(data):
    # The app is stopped. Only its own named preferences file is replaced, atomically.
    command="run-as com.drivemate.app --user 10 sh -c 'cat > shared_prefs/driving.xml.demo && chmod 600 shared_prefs/driving.xml.demo && mv shared_prefs/driving.xml.demo shared_prefs/driving.xml && rm -f shared_prefs/driving.xml.bak'"
    adb('shell',command,data=data)
    actual=adb('exec-out','run-as','com.drivemate.app','--user','10','cat','shared_prefs/driving.xml')
    if ET.tostring(ET.fromstring(actual))!=ET.tostring(ET.fromstring(data)):raise RuntimeError('Preference verification failed')

def main():
    parser=argparse.ArgumentParser();parser.add_argument('profile',choices=['arrival','departure','restore']);args=parser.parse_args()
    BACKUPS.mkdir(exist_ok=True)
    park()
    adb('shell','am','force-stop','--user','10','com.drivemate.app')
    try:
        current=adb('exec-out','run-as','com.drivemate.app','--user','10','cat','shared_prefs/driving.xml')
        stamp=datetime.datetime.now().strftime('%Y%m%d-%H%M%S-%f')
        snapshot=BACKUPS/(stamp+'-before-'+args.profile+'.xml');snapshot.write_bytes(current)
        manifest=json.loads(MANIFEST.read_text('utf-8')) if MANIFEST.exists() else {}
        if args.profile=='restore':
            if not manifest.get('active'):raise RuntimeError('No active sample data backup to restore')
            result=Path(manifest['original']).read_bytes();manifest['active']=False
        else:
            if not manifest.get('active'):manifest={'active':True,'original':str(snapshot)}
            original=Path(manifest['original']).read_bytes()
            root=seeded(ET.fromstring(original),args.profile,int(time.time()*1000))
            result=ET.tostring(root,encoding='utf-8',xml_declaration=True)
            manifest.update(profile=args.profile,latestBackup=str(snapshot))
        try:write_prefs(result)
        except Exception:
            write_prefs(current)
            raise
        MANIFEST.write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
        print('Applied profile: '+args.profile)
        print('Original data is backed up at: '+str(manifest.get('original',snapshot)))
    finally:
        adb('shell','am','start','--user','10','-W','-f','0x10008000','-n','com.drivemate.app/.MainActivity')
        api('/browser/session?client=drivemate-seed&closing=1','POST')

if __name__=='__main__':main()
