"""Zoom of the original ipe curves (true ipe coordinates) around a point, with control polygons."""
import sys, math
sys.path.insert(0,'.')
from compare import ipe_curves
path, cx, cy, r = sys.argv[1], float(sys.argv[2]), float(sys.argv[3]), float(sys.argv[4])
only = sys.argv[5].split(',') if len(sys.argv)>5 else None
COL={'red':'#E53935','blue':'#1E88E5','green':'#43A047','orange':'#FB8C00','yellow':'#C0A000','purple':'#8E24AA'}
def bez(ctrl):
    m=len(ctrl); d=[]
    for i in range(m):
        p0,p1,p2,p3=(ctrl[(i+k)%m] for k in range(4))
        b0=((p0[0]+4*p1[0]+p2[0])/6,(p0[1]+4*p1[1]+p2[1])/6); b1=((2*p1[0]+p2[0])/3,(2*p1[1]+p2[1])/3)
        b2=((p1[0]+2*p2[0])/3,(p1[1]+2*p2[1])/3); b3=((p1[0]+4*p2[0]+p3[0])/6,(p1[1]+4*p2[1]+p3[1])/6)
        if i==0: d.append("M%f %f"%(b0[0],-b0[1]))
        d.append("C%f %f %f %f %f %f"%(b1[0],-b1[1],b2[0],-b2[1],b3[0],-b3[1]))
    return " ".join(d)+" Z"
w=r/100
s='<svg xmlns="http://www.w3.org/2000/svg" width="1000" height="1000" viewBox="%f %f %f %f"><rect x="%f" y="%f" width="%f" height="%f" fill="white"/>'%(cx-r,-cy-r,2*r,2*r,cx-r,-cy-r,2*r,2*r)
for name,ctrl in ipe_curves(path):
    if only and name not in only: continue
    s+='<polyline points="%s" fill="none" stroke="%s" stroke-width="%f" stroke-dasharray="%f %f" opacity="0.5"/>'%(" ".join("%f,%f"%(x,-y) for x,y in ctrl+ctrl[:1]),COL[name],w/2,w,w)
    for x,y in ctrl: s+='<circle cx="%f" cy="%f" r="%f" fill="%s"/>'%(x,-y,w*1.5,COL[name])
    s+='<path d="%s" fill="none" stroke="%s" stroke-width="%f"/>'%(bez(ctrl),COL[name],w)
s+='</svg>'
out=sys.argv[6] if len(sys.argv)>6 else 'zoom.svg'
open(out,'w').write(s); print(out)
