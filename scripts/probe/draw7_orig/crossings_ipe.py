"""Count proper crossings between every pair of B-spline curves in a draw7 .ipe file (flattened), ignoring overlaps."""
import sys, re, itertools, math
sys.path.insert(0, '.')
from compare import ipe_curves
def flat(ctrl, k=64):
    m=len(ctrl); pts=[]
    for i in range(m):
        p0,p1,p2,p3=(ctrl[(i+j)%m] for j in range(4))
        for s in range(k):
            t=s/k
            b=[(1-t)**3, 3*t**3-6*t**2+4, -3*t**3+3*t**2+3*t+1, t**3]
            pts.append((sum(b[j]*p[0] for j,p in enumerate((p0,p1,p2,p3)))/6, sum(b[j]*p[1] for j,p in enumerate((p0,p1,p2,p3)))/6))
    return pts
def cross(a,b,c,d):
    def o(p,q,r): return (q[0]-p[0])*(r[1]-p[1])-(q[1]-p[1])*(r[0]-p[0])
    o1,o2,o3,o4=o(a,b,c),o(a,b,d),o(c,d,a),o(c,d,b)
    return o1*o2<0 and o3*o4<0
for path in sys.argv[1:]:
    cs=ipe_curves(path); fl=[(n,flat(c)) for n,c in cs]
    for (n1,p),(n2,q) in itertools.combinations(fl,2):
        hits=[]
        for i in range(len(p)):
            a,b=p[i-1],p[i]
            for j in range(len(q)):
                if cross(a,b,q[j-1],q[j]): hits.append((a,b))
        # count proper crossings, merging hits closer than 1e-3 of frame
        pts=[((a[0]+b[0])/2,(a[1]+b[1])/2) for a,b in hits]
        groups=[]
        for pt in pts:
            for g in groups:
                if math.dist(g,pt)<1.0: break
            else: groups.append(pt)
        print(path.split('/')[-1], n1, n2, "crossings:", len(groups), [(round(x,1),round(y,1)) for x,y in groups])
