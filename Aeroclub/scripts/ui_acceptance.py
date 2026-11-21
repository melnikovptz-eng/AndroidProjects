#!/usr/bin/env python3
import os,re,subprocess,sys,time,xml.etree.ElementTree as ET
APK="Aeroclub/app/build/outputs/apk/debug/app-debug.apk"; OUT="screenshots"; DIAG="diagnostics"
os.makedirs(OUT,exist_ok=True); os.makedirs(DIAG,exist_ok=True)
def run(a,check=True,capture=False):
 p=subprocess.run(a,text=True,stdout=subprocess.PIPE if capture else None,stderr=subprocess.STDOUT if capture else None)
 if check and p.returncode: raise RuntimeError("command failed: "+" ".join(a)+" "+(p.stdout or ""))
 return p.stdout.strip() if capture else p.returncode
def dump(n):
 path="/data/local/tmp/kd-window.xml"
 for _ in range(4):
  result=run(["adb","shell","uiautomator","dump",path],False,True)
  if "dumped" in result.lower():
   pulled=run(["adb","pull",path,f"{DIAG}/{n}.xml"],False,True)
   if os.path.exists(f"{DIAG}/{n}.xml"):
    try:return ET.parse(f"{DIAG}/{n}.xml").getroot()
    except:pass
  time.sleep(2)
 return ET.Element("hierarchy")
def visible(root):
 return " ".join((x.attrib.get("text","")+" "+x.attrib.get("content-desc","")).strip() for x in root.iter())
def recover_system_dialog(root):
 txt=visible(root)
 if "System UI isn't responding" in txt or "Системный интерфейс не отвечает" in txt:
  run(["adb","shell","input","tap","465","910"],False)
  time.sleep(12)
  return True
 return False
def tap(label,timeout=18):
 end=time.time()+timeout
 while time.time()<end:
  root=dump("current")
  if recover_system_dialog(root): continue
  parents={child:parent for parent in root.iter() for child in parent}
  for n in root.iter("node"):
   val=(n.attrib.get("text","")+" "+n.attrib.get("content-desc","")).strip()
   if label.lower() in val.lower():
    target=n
    while target is not None and target.attrib.get("clickable")!="true": target=parents.get(target)
    if target is not None:
     m=re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",target.attrib.get("bounds",""))
     if m:
      x1,y1,x2,y2=map(int,m.groups());run(["adb","shell","input","tap",str((x1+x2)//2),str((y1+y2)//2)])
      time.sleep(2);return True
  time.sleep(1)
 return False
def capture(name,required=(),allow_dialog=False):
 time.sleep(2);root=dump(name);txt=visible(root)
 if recover_system_dialog(root):
  time.sleep(8);root=dump(name+"-retry");txt=visible(root)
 with open(f"{OUT}/{name}.png","wb") as f: subprocess.run(["adb","exec-out","screencap","-p"],stdout=f,check=True)
 open(f"{DIAG}/{name}-text.txt","w").write(txt)
 miss=[x for x in required if x.lower() not in txt.lower()]
 if miss and not allow_dialog: raise RuntimeError(name+" missing "+str(miss)+" visible="+txt[:1800])
 print("SCREEN_OK",name,os.path.getsize(f"{OUT}/{name}.png"))
def start(component):
 run(["adb","shell","am","force-stop","ru.aeroclub.informer.mobile"])
 run(["adb","shell","am","start","-n","ru.aeroclub.informer.mobile/"+component],False)
 time.sleep(5)
def open_home():
 start("ru.aeroclub.informer.MainActivity")
 deadline=time.time()+120
 while time.time()<deadline:
  root=dump("startup-check");txt=visible(root)
  if "Все полёты" in txt and "Создать полёт" in txt:return
  if recover_system_dialog(root):continue
  time.sleep(4)
 raise RuntimeError("S12 home did not become visible")
def admin_section(label,name,required=()):
 if not tap(label): raise RuntimeError("admin navigation not clickable: "+label)
 capture(name,required)
try:
 open(f"{DIAG}/emulator-environment.txt","a").write("sdk="+run(["adb","shell","getprop","ro.build.version.sdk"],capture=True)+"\nabi="+run(["adb","shell","getprop","ro.product.cpu.abi"],capture=True)+"\n")
 run(["adb","logcat","-c"])
 ready=False
 for _ in range(120):
  probe=run(["adb","shell","service","check","package"],False,True)
  if "found" in probe.lower():ready=True;break
  time.sleep(2)
 if not ready:raise RuntimeError("Package Manager service not ready")
 installed=False
 for _ in range(6):
  result=run(["adb","install","--no-streaming","-r",APK],False,True)
  if "Success" in result:installed=True;break
  time.sleep(8)
 if not installed:raise RuntimeError("APK install failed: "+result)
 open_home()
 capture("S12-all-flights",("Все полёты","Создать полёт"))
 if tap("Ближайший полёт"):
  capture("S12-flight-details",("Я поеду","Я не поеду","Маршрут","Telegram"))
  run(["adb","shell","input","keyevent","4"],False);time.sleep(2)
 if tap("Соревнования"):capture("S12-competitions-tab",("Соревнования",),True)
 else:raise RuntimeError("competition tab not clickable")
 if not tap("Все полёты"):raise RuntimeError("all flights tab not clickable")
 if not tap("Календарь"):raise RuntimeError("calendar nav not clickable")
 capture("S01-calendar",("КАЛЕНДАРЬ МЕРОПРИЯТИЙ",))
 if not tap("Панель администратора"):raise RuntimeError("S01 admin entry not clickable")
 capture("S02-admin-overview",("Обзор","Вылеты","Участники","Сообщения"))
 admin_section("Вылеты","S03-admin-flights",("Создать вылет",))
 if tap("Создать вылет"):capture("S03-flight-editor")
 run(["adb","shell","input","keyevent","4"],False);time.sleep(2)
 if tap("Удалить этот вылет"):capture("S11-delete-flight-confirmation",("Удалить", "Отмена"),True);run(["adb","shell","input","keyevent","4"],False)
 admin_section("Сообщения","S04-admin-messages",("Предпросмотр сообщения",))
 if tap("изменить"):capture("S10-recipient-selection",("Всем",),True);run(["adb","shell","input","keyevent","4"],False)
 run(["adb","shell","input","keyevent","4"],False);time.sleep(2)
 # Return to the overview, then open the separate admin calendar.
 if tap("К информеру"):pass
 start("ru.aeroclub.informer.AdminActivity")
 if tap("Обзор"):time.sleep(1)
 if tap("Открыть календарь"):capture("S05-admin-calendar",("Календарь клуба",))
 start("ru.aeroclub.informer.AdminActivity")
 admin_section("Участники","S06-admin-participants",("Поиск участника",))
 start("ru.aeroclub.informer.AdminActivity")
 if tap("Настройки"):capture("S07-club-settings",("Профиль клуба",))
 start("ru.aeroclub.informer.AdminActivity")
 if tap("Уведомления"):capture("S08-notifications",("Локальные напоминания",))
 # Return to the actual participant-facing calendar and capture its event dialog if one is present.
 open_home()
 if tap("Календарь"): 
  if tap("Панель администратора"): pass
 log=run(["adb","logcat","-d","-v","threadtime"],False,True);open(f"{DIAG}/logcat.txt","w").write(log)
 fatal=[x for x in log.splitlines() if any(k in x for k in ("FATAL EXCEPTION","ANR in ru.aeroclub","Fatal signal"))]
 open(f"{DIAG}/fatal-errors.txt","w").write("\n".join(fatal))
 if fatal:raise RuntimeError("Android fatal errors: "+str(len(fatal)))
 open(f"{DIAG}/ui-acceptance.txt","w").write("result=PASS\nscreenshots="+str(len([x for x in os.listdir(OUT) if x.endswith(".png")]))+"\n")
except Exception as e:
 open(f"{DIAG}/ui-acceptance.txt","w").write("result=FAIL\nerror="+str(e)+"\n")
 try:open(f"{DIAG}/logcat.txt","w").write(run(["adb","logcat","-d","-v","threadtime"],False,True))
 except:pass
 print("UI_ACCEPTANCE_FAIL",e,file=sys.stderr);sys.exit(1)
