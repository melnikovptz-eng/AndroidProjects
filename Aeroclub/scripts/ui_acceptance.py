#!/usr/bin/env python3
import os,re,subprocess,sys,time,xml.etree.ElementTree as ET
APK="Aeroclub/app/build/outputs/apk/debug/app-debug.apk"; OUT="screenshots"; DIAG="diagnostics"
os.makedirs(OUT,exist_ok=True); os.makedirs(DIAG,exist_ok=True)
def run(a,check=True,capture=False):
 p=subprocess.run(a,text=True,stdout=subprocess.PIPE if capture else None,stderr=subprocess.STDOUT if capture else None)
 if check and p.returncode: raise RuntimeError("command failed: "+" ".join(a)+" "+(p.stdout or ""))
 return p.stdout.strip() if capture else p.returncode
def dump(n):
 run(["adb","shell","uiautomator","dump","/sdcard/kd-window.xml"],False)
 run(["adb","pull","/sdcard/kd-window.xml",f"{DIAG}/{n}.xml"],False)
 try:return ET.parse(f"{DIAG}/{n}.xml").getroot()
 except:return ET.Element("hierarchy")
def visible(root):
 return " ".join((x.attrib.get("text","")+" "+x.attrib.get("content-desc","")).strip() for x in root.iter())
def tap(label):
 end=time.time()+18
 while time.time()<end:
  root=dump("current")
  parents={child:parent for parent in root.iter() for child in parent}
  for n in root.iter("node"):
   val=(n.attrib.get("text","")+" "+n.attrib.get("content-desc","")).strip()
   if label in val:
    target=n
    while target is not None and target.attrib.get("clickable")!="true": target=parents.get(target)
    if target is not None:
     m=re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",target.attrib.get("bounds",""))
     if m:
      x1,y1,x2,y2=map(int,m.groups());run(["adb","shell","input","tap",str((x1+x2)//2),str((y1+y2)//2)]);time.sleep(3);return True
  time.sleep(1)
 return False
def capture(name,required=()):
 time.sleep(2);root=dump(name);txt=visible(root)
 with open(f"{OUT}/{name}.png","wb") as f: subprocess.run(["adb","exec-out","screencap","-p"],stdout=f,check=True)
 open(f"{DIAG}/{name}-text.txt","w").write(txt)
 miss=[x for x in required if x not in txt]
 if miss:raise RuntimeError(name+" missing "+str(miss)+" visible="+txt[:1500])
 print("SCREEN_OK",name,os.path.getsize(f"{OUT}/{name}.png"))
def home():
 run(["adb","shell","am","force-stop","ru.aeroclub.informer.mobile"])
 run(["adb","shell","am","start","-n","ru.aeroclub.informer.mobile/ru.aeroclub.informer.MainActivity"],False)
 deadline=time.time()+150
 while time.time()<deadline:
  root=dump("startup-check")
  if "Все полёты" in visible(root) and "Создать полёт" in visible(root): break
  time.sleep(8)
try:
 open(f"{DIAG}/emulator-environment.txt","w").write("sdk="+run(["adb","shell","getprop","ro.build.version.sdk"],capture=True)+"\nabi="+run(["adb","shell","getprop","ro.product.cpu.abi"],capture=True)+"\n")
 run(["adb","logcat","-c"])
 ready=False
 for attempt in range(1,181):
  probe=run(["adb","shell","service","check","package"],False,True)
  if "found" in probe.lower():
   ready=True;break
  time.sleep(2)
 if not ready: raise RuntimeError("Package Manager service not ready after 360 seconds")
 installed=False
 for attempt in range(1,7):
  result=run(["adb","install","--no-streaming","-r",APK],False,True)
  if "Success" in result: installed=True;break
  time.sleep(10)
 if not installed: raise RuntimeError("APK install failed after retries: "+result)
 home()
 capture("S12-all-flights",("Все полёты","Создать полёт"))
 if tap("Соревнования"):capture("S12-competitions",("Соревнования",))
 else:raise RuntimeError("competition tab not clickable")
 if not tap("Все полёты"):raise RuntimeError("all flights tab not clickable")
 if not tap("Календарь"):raise RuntimeError("calendar nav not clickable")
 capture("S01-calendar",("КАЛЕНДАРЬ МЕРОПРИЯТИЙ",))
 if not tap("Панель администратора"):raise RuntimeError("admin button not clickable")
 capture("S02-admin-overview")
 home()
 if tap("Участники"):capture("S06-participants")
 home()
 if tap("Настройки"):capture("S07-settings")
 log=run(["adb","logcat","-d","-v","threadtime"],False,True);open(f"{DIAG}/logcat.txt","w").write(log)
 fatal=[x for x in log.splitlines() if any(k in x for k in ("FATAL EXCEPTION","ANR in ","Fatal signal","AndroidRuntime"))]
 open(f"{DIAG}/fatal-errors.txt","w").write("\n".join(fatal))
 if fatal:raise RuntimeError("Android fatal errors: "+str(len(fatal)))
 open(f"{DIAG}/ui-acceptance.txt","w").write("result=PASS\n")
except Exception as e:
 open(f"{DIAG}/ui-acceptance.txt","w").write("result=FAIL\nerror="+str(e)+"\n")
 try:open(f"{DIAG}/logcat.txt","w").write(run(["adb","logcat","-d","-v","threadtime"],False,True))
 except:pass
 print("UI_ACCEPTANCE_FAIL",e,file=sys.stderr);sys.exit(1)
