import json,urllib.request,math,time
def route(pts):
    s=";".join(f"{lng},{lat}" for lat,lng in pts)
    u=f"https://routing.openstreetmap.de/routed-foot/route/v1/driving/{s}?overview=full&geometries=geojson"
    d=json.load(urllib.request.urlopen(u,timeout=30))
    return [(lat,lng) for lng,lat in d['routes'][0]['geometry']['coordinates']]
def dist(a,b):
    R=6371008.8;p1,p2=math.radians(a[0]),math.radians(b[0]);dl=math.radians(b[1]-a[1]);dp=p2-p1
    h=math.sin(dp/2)**2+math.cos(p1)*math.cos(p2)*math.sin(dl/2)**2;return 2*R*math.asin(math.sqrt(h))
def resample(pts,step=15):
    out=[pts[0]];carry=0
    for a,b in zip(pts,pts[1:]):
        d=dist(a,b);t=step-carry
        while t<=d:
            f=t/d;out.append((a[0]+(b[0]-a[0])*f,a[1]+(b[1]-a[1])*f));t+=step
        carry=d-(t-step)
    out.append(pts[-1]);return out
def elev(pts):
    res=[]
    for i in range(0,len(pts),100):
        chunk=pts[i:i+100];q="|".join(f"{a:.6f},{b:.6f}" for a,b in chunk)
        d=json.load(urllib.request.urlopen(f"https://api.opentopodata.org/v1/srtm30m?locations={q}",timeout=60))
        res+=[r['elevation'] for r in d['results']];time.sleep(1.1)
    return res
R=[
 dict(id="bukhan",name="북한산 백운대",purpose="계속 오르막 · 최고 고도가 꾸준히 오르는지",way=[(37.6595,126.9500),(37.6587,126.9776)]),
 dict(id="inwang",name="인왕산 넘어가기",purpose="올랐다 내려오기 · 내려와도 최고 고도가 줄지 않는지",way=[(37.5757,126.9680),(37.5852,126.9578),(37.5838,126.9652)]),
 dict(id="yeouido",name="여의도공원 한 바퀴",purpose="짧고 평평 · 거리·시간 형식, 사진 마커",way=[(37.5263,126.9208),(37.5290,126.9236),(37.5256,126.9270),(37.5231,126.9240),(37.5263,126.9208)]),
 dict(id="gwanak",name="관악산 종주 (서울대→연주대→과천)",purpose="긴 산행 · 장시간 기록, 백그라운드",way=[(37.4697,126.9482),(37.4434,126.9637),(37.4354,126.9886)]),
]
out=[]
for r in R:
    pts=resample(route(r['way']))
    if r['id']=="yeouido": pts=resample(r['way'])  # 공원 둘레는 직접 찍은 점 그대로
    el=elev(pts)
    total=sum(dist(a,b) for a,b in zip(pts,pts[1:]))
    out.append(dict(id=r['id'],name=r['name'],purpose=r['purpose'],distanceMeters=round(total),
        minEle=min(el),maxEle=max(el),points=[[round(a,6),round(b,6),e] for (a,b),e in zip(pts,el)]))
    print(r['name'],round(total),"m",min(el),"→",max(el),"start",el[0],"end",el[-1],len(pts))
json.dump(out,open("routes.json","w"),ensure_ascii=False)
