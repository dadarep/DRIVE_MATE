"""PC-only demo: console speed -> road-route GPS; never edit app telemetry."""
import argparse,bisect,json,math,os,subprocess,time,urllib.request
from pathlib import Path
import xml.etree.ElementTree as ET
ADB=Path(os.environ['LOCALAPPDATA'])/'Android/Sdk/platform-tools/adb.exe'
STATE=Path(os.environ['TEMP'])/'drivemate-console-gps'
PID=STATE.with_suffix('.pid'); STOP=STATE.with_suffix('.stop'); LOG=STATE.with_suffix('.log')
BASE='http://127.0.0.1:18088'
def log(message):
    with LOG.open('a',encoding='utf-8') as f:f.write(time.strftime('%Y-%m-%d %H:%M:%S ')+message+'\n')
def adb(*args):
    r=subprocess.run([str(ADB),'-s','emulator-5554',*map(str,args)],capture_output=True,timeout=8,creationflags=subprocess.CREATE_NO_WINDOW)
    if r.returncode:raise RuntimeError(r.stderr.decode('utf-8',errors='replace'))
    return r.stdout.decode('utf-8')
def preferences():
    root=ET.fromstring(adb('exec-out','run-as','com.drivemate.app','--user','10','cat','shared_prefs/driving.xml'))
    return {e.attrib['name']:e.text if e.tag=='string' else e.attrib.get('value') for e in root}
def distance(a,b):
    y=math.radians(b[1]-a[1]);x=math.radians(b[0]-a[0])
    h=math.sin(y/2)**2+math.cos(math.radians(a[1]))*math.cos(math.radians(b[1]))*math.sin(x/2)**2
    return 12742000*math.asin(math.sqrt(min(1,max(0,h))))
def point_at(points,lengths,meters):
    meters=min(lengths[-1],max(0,meters))
    i=min(len(points)-2,max(0,bisect.bisect_right(lengths,meters)-1))
    ratio=(meters-lengths[i])/max(.001,lengths[i+1]-lengths[i])
    return tuple(points[i][j]+(points[i+1][j]-points[i][j])*ratio for j in (0,1))
def next_progress(progress,speed,gear,dt,total,active):
    if not active or gear not in ('Drive','Reverse'):return progress
    return min(total,max(0,progress+(1 if gear=='Drive' else -1)*max(0,speed)/3.6*min(2,max(0,dt))))
def road_route(current,target):
    url=f'https://router.project-osrm.org/route/v1/driving/{current[0]},{current[1]};{target[0]},{target[1]}?overview=full&geometries=geojson&steps=false'
    with urllib.request.urlopen(url,timeout=15) as r:data=json.load(r)
    if data.get('code')!='Ok':raise RuntimeError('Road route unavailable')
    points=data['routes'][0]['geometry']['coordinates']
    if distance(current,points[0])>.5:points=[current,*points]
    if len(points)<2:raise RuntimeError('No road geometry')
    lengths=[0.0]
    for a,b in zip(points,points[1:]):lengths.append(lengths[-1]+distance(a,b))
    return points,lengths
def route_point(value):
    try:
        lat,lon=map(float,value.split(','))
        if not (math.isfinite(lat) and math.isfinite(lon) and -85<=lat<=85 and -180<=lon<=180): raise ValueError()
        return lon,lat
    except (ValueError,TypeError):
        raise argparse.ArgumentTypeError('Use latitude,longitude with latitude -85..85 and longitude -180..180')

def main():
    parser=argparse.ArgumentParser(description='Explicit emulator-only road-route GPS simulation')
    parser.add_argument('--origin',type=route_point,help='latitude,longitude; supply with --destination for signed release APKs')
    parser.add_argument('--destination',type=route_point,help='latitude,longitude; route must match the app destination')
    args=parser.parse_args()
    if (args.origin is None)!=(args.destination is None): parser.error('--origin and --destination must be supplied together')
    fixed=args.origin is not None
    fd=os.open(PID,os.O_CREAT|os.O_EXCL|os.O_WRONLY)
    with os.fdopen(fd,'w') as f:f.write(str(os.getpid()))
    STOP.unlink(missing_ok=True)
    log('START: explicit GPS simulation; no app preference or vehicle-control writes')
    current=args.origin;target=args.destination;points=None;lengths=None;progress=0.0
    last=time.monotonic();last_prefs=0;last_log=0;last_error=0;retry_at=0;paused=False;arrived=False
    try:
        adb('forward','tcp:18088','tcp:8088')
        while not STOP.exists():
            tick=time.monotonic()
            try:
                if fixed and points is None and tick>=retry_at:
                    retry_at=tick+30;points,lengths=road_route(current,target)
                    log(f'ROUTE: {lengths[-1]:.0f}m; explicit release-compatible simulation, app preferences not accessed')
                    last=time.monotonic()
                if not fixed and tick-last_prefs>=2:
                    prefs=preferences();last_prefs=tick
                    paused=prefs.get('recordingPaused')=='true'
                    if current is None:current=(float(prefs['liveLon']),float(prefs['liveLat']))
                    requested=(float(prefs['destinationLon']),float(prefs['destinationLat'])) if prefs.get('destinationLat') else None
                    if requested!=target or (target is not None and points is None and tick>=retry_at):
                        target=requested;points=None;lengths=None;progress=0;arrived=False
                        if target is not None:
                            retry_at=tick+30;points,lengths=road_route(current,target)
                            log(f'ROUTE: {lengths[-1]:.0f}m; follows the app destination')
                        last=time.monotonic()
                with urllib.request.urlopen(BASE+'/vehicle/state',timeout=3) as r:state=json.load(r)
                controls=state.get('controls',{})
                gear=(state.get('gear',{}).get('actual') or {}).get('value','Park')
                speed=float(controls.get('speed_kph',0));active=bool(controls.get('emulator_active')) and not paused
                now=time.monotonic();dt=now-last;last=now
                if points is not None and current is not None and not paused:
                    progress=next_progress(progress,speed,gear,dt,lengths[-1],active)
                    current=point_at(points,lengths,progress)
                    moving=active and gear in ('Drive','Reverse') and 0<progress<lengths[-1]
                    adb('emu','geo','fix',f'{current[0]:.8f}',f'{current[1]:.8f}',30,8,f'{speed/1.852 if moving else 0:.3f}')
                    if progress>=lengths[-1] and not arrived:
                        arrived=True;log('ARRIVED: GPS holds at route end; select Park to finish the trip')
                    if now-last_log>=10:
                        last_log=now;log(f'{gear} {speed:.0f}km/h; GPS simulation {progress:.0f}/{lengths[-1]:.0f}m; active={active}')
            except Exception as e:
                last=time.monotonic()
                if tick-last_error>10:log(type(e).__name__+': '+str(e));last_error=tick
            time.sleep(max(.05,.5-(time.monotonic()-tick)))
    finally:
        PID.unlink(missing_ok=True);STOP.unlink(missing_ok=True);log('STOP: no further simulated GPS fixes')
if __name__=='__main__':main()
