from itertools import combinations,permutations
from copy import copy,deepcopy
import networkx as nx

IPE_COLORS = [
'red',
'green',
'blue',
'orange',
'yellow',
'purple',
'black',
'darkgreen',
'darkred',
'darkblue',
'darkorange',
'turquoise',
'darkgray']
IPE_COLORS += ['COLOR'+str(i) for i in range(20)]  # additional "virtual" colors



def get_rotation_system(G):
	rotation_system = dict()
	V = G.vertices()
	pos = G.get_pos()
	pos = {v:vector(RR,pos[v]) for v in pos} # use sage vectors over real numbers
	for v in V:
		Nv = G.neighbors(v)
		rotation_system[v] = sorted(Nv, key=lambda u: -atan2(*(pos[u]-pos[v])))
	return rotation_system



# MS 07.10.2017 TODO: check this! not 100% sure about this code
def directed_subgraph_from_colors(G,colrs):
	edges2 = copy(G.edges())
	m = len(edges2)

	mapping = {v:v for v in G.vertices()}

	for i in range(m):
		u,v,c = edges2[i]
		if c not in colrs:
			# contract 
			for j in range(m):
				u2,v2,c2 = edges2[j]
				if u2 == v: 
					mapping[u2] = u
					u2 = u
				if v2 == v: 
					mapping[v2] = u
					v2 = u
				edges2[j] = u2,v2,c2

	# transitive closure of mapping...
	changes = True
	while changes:
		changes = False
		for v in mapping:
			if mapping[v] != mapping[mapping[v]]:
				mapping[v] = mapping[mapping[v]]
				changes = True

	H = DiGraph(edges2,loops=True,multiedges=True)
	H.remove_loops()
	H.remove_multiple_edges()	

	reverse_mapping = {u:tuple(v for v in G if mapping[v] == u) for u in H}
	H.relabel({u:reverse_mapping[u] for u in H})
	return DiGraph(H,loops=False,multiedges=False)


def compute_triples_from_pca(n,G0,outer_face_vertex=None):
	G = copy(G0)
	colors = list(set(G.edge_labels()))
	assert(len(colors) == n)
	N = range(n)	

	G.set_planar_positions() # compute embedding (which is unique)

	if outer_face_vertex == None: outer_face_vertex = 0
	#print "outer_face_vertex",outer_face_vertex

	# phase 1 : orient arcs towards "outer face vertex"
	for c0 in colors:
		G_c0 = Graph([(u,v) for (u,v,c) in G.edges() if c != c0])
		for u,v,c in G.edges():
			if c == c0:
				if G_c0.distance(v,outer_face_vertex) == Infinity:
					G.delete_edge(u,v)
					G.add_edge(v,u,c)

	COLORMAP={c:c for c in colors}
	#G.plot(edge_colors=G._color_by_label(COLORMAP)).save("foo.png")

	# before continuing, make sure that all faces but one 
	# (which is the outer face of the dual graph) are oriented in the same way 
	F = G.faces()
	pos = G.get_pos()
	positive = 0
	negative = 0
	for f in F:
		assert(len(f)==4) # assert that dual graph is quadrangulation
		X = [pos[u][0] for (u,v) in f]
		Y = [pos[u][1] for (u,v) in f]
		area = sum(X[i]*Y[i-1]-X[i-1]*Y[i] for i in range(len(f))) # actually twice the area, should be divided by 2
		if area > 0: positive += 1
		if area < 0: negative += 1
	assert(negative == 1 or positive == 1)


	# phase 2 : compute triples
	containment = []
	for k in N:
		G_k = Graph([(u,v) for (u,v,c) in G.edges() if c != colors[k]])
		for f in F: 
			ff = [u for (u,v) in f]
			for i in range(4):
				u,v,w = ff[i-2],ff[i-1],ff[i]
				if G.has_edge(v,u) and G.has_edge(v,w):
					cvu = G.edge_label(v,u)
					cvw = G.edge_label(v,w)
					assert(cvu != cvw)
					i = colors.index(cvu)
					j = colors.index(cvw)
					if k in [i,j]: continue 

					if G_k.distance(outer_face_vertex,v) == Infinity: 
						# face lies inside the k-th pseudocircle
						assert(G_k.distance(outer_face_vertex,u) == Infinity)
						assert(G_k.distance(outer_face_vertex,w) == Infinity)
						containment.append((i,j,k))

	containment.sort()
	#print "containment",containment

	def _compute_triple(a,b,c):
		containment_abc = [ijk for ijk in containment if a in ijk and b in ijk and c in ijk]
		lc = len(containment_abc)
		C_containing = [k for (i,j,k) in containment_abc]
		H = directed_subgraph_from_colors(G,[colors[a],colors[b],colors[c]])
		H.set_planar_positions()

		if lc == 3 or lc == 0:
			# Krupp and Typ I require additional information 
			# about the cyclic order of circles (mirroring makes a difference)

			sign = +1
			embedding_H = get_rotation_system(Graph(H))
			for u in H.vertices():
				if outer_face_vertex in u:
					assert(len(embedding_H[u]) == 3)
					order = []
					for v in embedding_H[u]:
						assert(H.has_edge(v,u))
						order.append(colors.index(H.edge_label(v,u)))
					assert(set(order)=={a,b,c})
					i = order.index(a)
					order = order[i:]+order[:i]
					if order[1] == c:
						sign *= -1
					break

		#H.plot(edge_colors=H._color_by_label(COLORMAP)).save("foo"+str((a,b,c))+".png")

		if lc == 3: 
			return "K"+('+' if sign > 0 else '-')
		elif lc == 0: 
			return "I"+('+' if sign > 0 else '-')
		elif lc == 2: 
			return "II"+str(C_containing[0])
		elif lc == 4: 
			return "III"+str([k for k in [a,b,c] if k not in C_containing][0])
		else:
			exit("Error?")


	triples = {abc:_compute_triple(*abc) for abc in permutations(N,3)}
	return triples


def subgraph_from_colors(G,colrs):
	edges2 = copy(G.edges())
	m = len(edges2)
	for i in range(m):
		u,v,c = edges2[i]
		if u > v: u,v = v,u
		assert(u<=v)
		if c not in colrs:
			# contract
			for j in range(m):
				u2,v2,c2 = edges2[j]
				if u2 == v: u2 = u
				if v2 == v: v2 = u
				edges2[j] = u2,v2,c2

	H = Graph(edges2,loops=True,multiedges=True)
	H.remove_loops()
	H.remove_multiple_edges()
	return H

def subgraph(G,colors,k):
	#print "edges",edges
	for colrs in combinations(colors,k):
		H = subgraph_from_colors(G,colrs)
		yield colrs,H

# G .. dual graph, v .. triangle
def flip_triangle(G,v):
	nv = G.neighbors(v)
	assert(len(nv) == 3)
	n1,n3,n5 = nv
	c1 = G.edge_label(v,n1)
	c3 = G.edge_label(v,n3)
	c5 = G.edge_label(v,n5)

	n2 = [w for w in G.neighbors(n1) if G.edge_label(n1,w) == c3 and G.has_edge(n3,w)]
	n4 = [w for w in G.neighbors(n3) if G.edge_label(n3,w) == c5 and G.has_edge(n5,w)]
	n6 = [w for w in G.neighbors(n5) if G.edge_label(n5,w) == c1 and G.has_edge(n1,w)]

	assert(len(n2)==1)
	assert(len(n4)==1)
	assert(len(n6)==1)
	n2 = n2[0]
	n4 = n4[0]
	n6 = n6[0]
	G.delete_edge(v,n1)
	G.delete_edge(v,n3)
	G.delete_edge(v,n5)
	G.add_edge(v,n2,c5)
	G.add_edge(v,n4,c1)
	G.add_edge(v,n6,c3)
	return G


def all_possible_triangle_flips(g,digonfree=False):
	for v in g.vertices():
		if g.degree(v) == 3:
			g2 = g.copy()
			#g2 = g
			g2 = flip_triangle(g2,v)
			
			if not digonfree or 2 not in g2.degree(): 
				#x=g2.canonical_label(algorithm="bliss")
				yield g2.canonical_label(algorithm="sage")

			#g2 = flip_triangle(g2,v)



def color_graph(g):
	arcs = {}
	uncolored = set(g.edges(labels=False))
	#print uncolored

	#g.set_planar_positions() 
	# MS 29.10.2017 : this is bad for computation time! 
	# the embedding is sufficient and can be computed 
	# using a different algorithm! the set_embedding 
	# method is automatically called by the face method
	
	faces = g.faces()
	for f in faces:
		assert(len(f) == 4)

	unused_colors = list(reversed(IPE_COLORS))
	while uncolored:
		(u,v) = e1 = e0 = uncolored.pop()
		uncolored.add(e0)
		current_color = unused_colors.pop()
		while True:
			e1r = (v,u)
			if e1 in uncolored:
				arcs[e1] = current_color
				uncolored.remove(e1)
			else:
				assert(e1r in uncolored)
				arcs[e1] = current_color
				uncolored.remove(e1r)

			next_face = None
			for f in faces:
				if e1r in f:
					next_face = f
					break
			assert(next_face)
			i = next_face.index(e1r)
			e1 = (u,v) = next_face[i-2] # next_edge
			if e1 == e0: break # cycle done!

	return arcs



def triples_from_fingerprint(fingerprint):
	triples = {}
	for a,b,c in combinations(fingerprint,3):
		b1 = fingerprint[a].index(b)
		b2 = fingerprint[a].index(-b)
		c1 = fingerprint[a].index(c)
		c2 = fingerprint[a].index(-c)
		l = len(fingerprint[a])
		i1 = c1 in cyclic_range(b1,b2,l)
		i2 = c2 in cyclic_range(b1,b2,l)
		if (i1 and i2) or (not i1 and not i2):
			triples[a,b,c] = -1
		else:
			triples[a,b,c] = +1
	return triples


def incident_k_gons(arcs,deg):
	colors = list(set(arcs.values()))
	n = len(colors)
	ikgons = {c:{k:0 for k in range(2*n+1)} for c in colors}

	for (u,v) in arcs:
		c = arcs[(u,v)]
		ikgons[c][deg[u]] += 1
		ikgons[c][deg[v]] += 1
	
	return ikgons


def vertices_from_arcs(arcs):
	m = max(max(a) for a in arcs)+1
	return range(m)


def common_intersection(vertices,arcs,outer_vertex=None):
	partitions = compute_partitions(vertices,arcs)
	possible_separations = []
	for u in vertices:
		if outer_vertex != None and u != outer_vertex: 
			continue
		if partitions[u]:
			assert(len(partitions[u]) == 1)
			v = list(partitions[u])[0]
			possible_separations.append((u,v))

	return possible_separations


def compute_partitions(vertices,arcs):
	colors = list(set(arcs.values()))
	component = dict()
	component_inverse = dict()
	for c1 in colors:
		arcs2 = [e for e in arcs if arcs[e] != c1]
		component[c1],component_inverse[c1] = connected_components(vertices,arcs2)
		for i in range(2):
			component_inverse[c1][i] = set(component_inverse[c1][i])

	others = {}
	for u in vertices:
		others[u] = set(vertices)
		for c1 in colors:
			if component[c1][u] == 0:
				assert(u not in component_inverse[c1][1])
				others[u] &= component_inverse[c1][1]
			else:
				assert(u not in component_inverse[c1][0])
				others[u] &= component_inverse[c1][0]
			
		for v in others[u]:
			for c1 in colors:
				assert(component[c1][u] != component[c1][v])

		assert(len(others[u]) <= 1)
	return others


def compute_primal_graph(arcs,arcs_with_color=None):
	if arcs_with_color == None:
		arcs_with_color = compute_circles_of_arcs(arcs)

	colors = list(set(arcs.values()))
	color_val = {c:1+colors.index(c) for c in colors}

	primal_vertices = [(c1,c2) for c1,c2 in permutations(colors,2)]
	primal_arcs = {}
	print ("primal_vertices",len(primal_vertices),primal_vertices)

	colseq = {}
	for c1 in colors:
		colseq[c1] = []
		l = len(arcs_with_color[c1])
		for i in range(l):
			j = i-1
			uj,vj = arcs_with_color[c1][j]
			ui,vi = arcs_with_color[c1][i]
			assert (uj,ui) in arcs or (ui,uj) in arcs
			if (uj,ui) in arcs:
				c2 = arcs[(uj,ui)]
				colseq[c1].append(primal_vertices.index((c1,c2)))
			if (ui,uj) in arcs:
				c2 = arcs[(ui,uj)]
				colseq[c1].append(primal_vertices.index((c2,c1)))

		for i in range(l):
			primal_arcs[(colseq[c1][i-1],colseq[c1][i])] = c1

	print ("primal_arcs",len(primal_arcs),primal_arcs)
	return primal_arcs,colseq
	


def normalize_cyclic_list(tup):
	return min([tup[i0:]+tup[:i0] for i0 in range(len(tup))])


def normalize_fingerprint(fingerprint):
	n = len(fingerprint)
	assert(set(fingerprint) == set(range(1,n+1)))

	i2 = fingerprint[1].index(2)
	perm = {1:1}
	for k in fingerprint[1][i2:]+fingerprint[1][:i2]:
		if abs(k) not in perm.values():
			perm[len(perm)+1] = abs(k)

	assert(set(perm.values()) == set(fingerprint.keys()))

	if DEBUG: print (fingerprint_to_string(fingerprint))
	fp2 = relabel_fingerprint(fingerprint,perm)
	if DEBUG: print (fingerprint_to_string(fp2))

	for u in fp2:
		if u == 1:
			i2 = fp2[u].index(2)
			fp2[u] = fp2[u][i2:]+fp2[u][:i2]
		else:
			i1 = fp2[u].index(1)
			fp2[u] = fp2[u][i1:]+fp2[u][:i1]
	
	assert(fp2[1][0] == 2)
	return fp2


def sign(x): 
	return (x > 0) - (x < 0)


def relabel_fingerprint(fingerprint,permutation):
	return {permutation[i]:[permutation[abs(j)]*sign(j) for j in fingerprint[i]] for i in fingerprint}


def lexmin_fingerprint(fingerprint):
	n = len(fingerprint)
	N = fingerprint.keys()
	assert(N == range(1,n+1))

	best = fingerprint

	for i in N:
		for j in N:
			if j != i: 
				perm = [i,j]+[k for k in N if k != i and k != j]
				perm = {k+1:perm[k] for k in range(len(perm))}
				fp2 = relabel_fingerprint(fingerprint,perm)
				fp2 = normalize_fingerprint(fp2)

				for c in fp2:
					if fp2[c] < best[c]:
						best = fp2
						break
					if fp2[c] > best[c]:
						break

	exit("lexmin_fingerprint: lexmin fingerprints are currently not used and not properly implemented. (directions of circles...?)")
	return best


def fingerprint_to_string(fingerprint):
	return '\n'.join(str(c)+':'+' '.join(str(x).rjust(2) for x in fingerprint[c]) for c in fingerprint)


def compute_fingerprint(arcs,arcs_with_color=None):
	if arcs_with_color == None:
		arcs_with_color = compute_circles_of_arcs(arcs)

	colors = list(set(arcs.values()))
	color_val = {c:1+colors.index(c) for c in colors}

	fingerprint = {}
	n = len(colors)
	for c in colors:
		cv = color_val[c]
		#cv = c
		fingerprint[cv] = []
		for j in range(2*(n-1)):
			# re-check order
			ui,vi = arcs_with_color[c][j-1]
			uj,vj = arcs_with_color[c][j]
			assert ((ui,uj) in arcs and (vi,vj) in arcs) or ((uj,ui) in arcs and (vj,vi) in arcs)
			if (ui,uj) in arcs:
				fingerprint[cv].append(+color_val[arcs[(ui,uj)]])
				#if arcs[(ui,uj)] in fingerprint:
				#	fingerprint[cv].append('+'+arcs[(ui,uj)])
			else:
				fingerprint[cv].append(-color_val[arcs[(uj,ui)]])
				#if arcs[(uj,ui)] in fingerprint:
				#	fingerprint[cv].append('-'+arcs[(uj,ui)])

	return fingerprint


def compute_fingerprint_compactcolors(arcs,arcs_with_color=None):
	if arcs_with_color == None:
		arcs_with_color = compute_circles_of_arcs(arcs)

	colors = list(set(arcs.values()))
	color_val = {c:1+colors.index(c) for c in colors}

	fingerprint = {}
	n = len(colors)
	for c in colors:
		cv = c
		fingerprint[cv] = []
		for j in range(2*(n-1)):
			# re-check order
			ui,vi = arcs_with_color[c][j-1]
			uj,vj = arcs_with_color[c][j]
			assert ((ui,uj) in arcs and (vi,vj) in arcs) or ((uj,ui) in arcs and (vj,vi) in arcs)
			if (ui,uj) in arcs:
				if arcs[(ui,uj)] in fingerprint:
					fingerprint[cv].append('+'+arcs[(ui,uj)])
			else:
				if arcs[(uj,ui)] in fingerprint:
					fingerprint[cv].append('-'+arcs[(uj,ui)])

	return fingerprint


def compute_circles_of_arcs(arcs):
	colors = list(set(arcs.values()))
	n = len(colors)

	# sort arcs for color
	arcs_with_color = {}
	for c in colors:
		arcs_with_color[c] = []

	for a in arcs:
		arcs_with_color[arcs[a]].append(a)

	for c in colors:
		assert(len(arcs_with_color[c]) == 2*(n-1))

	if DEBUG: print (arcs_with_color)


	for c in colors:
		for i in range(2*(n-1)-1):
			ui,vi = arcs_with_color[c][i]
			found = False
			for j in range(i+1,2*(n-1)):
				uj,vj = arcs_with_color[c][j]
				if ((ui,uj) in arcs and (vi,vj) in arcs) or ((uj,ui) in arcs and (vj,vi) in arcs):
					# in both cases exchange
					arcs_with_color[c][i+1],arcs_with_color[c][j] = arcs_with_color[c][j],arcs_with_color[c][i+1]
					found = True
					break
			assert(found)

		# all circles must have the same orientation
		if c != colors[0]: 
			for j in range(2*(n-1)):
				ui,vi = arcs_with_color[c][j-1]
				uj,vj = arcs_with_color[c][j]
				if (ui,uj) in arcs and arcs[(ui,uj)] == colors[0]:
					assert((vi,vj) in arcs_with_color[colors[0]])
					assert((ui,uj) in arcs_with_color[colors[0]])
					iv = arcs_with_color[colors[0]].index((vi,vj))
					iu = arcs_with_color[colors[0]].index((ui,uj))
					divu = (iu-iv) % (2*n-2)
					assert(divu in [1,2*n-3])
					if divu != 1:
						# change direction of current circle
						arcs_with_color[c].reverse()
					break

	# re-check order
	for c in colors:
		for j in range(2*(n-1)):
			ui,vi = arcs_with_color[c][j-1]
			uj,vj = arcs_with_color[c][j]
			assert ((ui,uj) in arcs and (vi,vj) in arcs) or ((uj,ui) in arcs and (vj,vi) in arcs)

	return arcs_with_color




def validTriples(n,usedcolors,kruppOnly,nonKruppOnly,checklastonly=False):
	if usedcolors and (kruppOnly or nonKruppOnly):
		valid = True
		for b in IPE_COLORS[:n]:
			if checklastonly and b != usedcolors[-1][1:]: continue
			for c in IPE_COLORS[:n]:
				if b == c: continue
				try:
					b1 = usedcolors.index("+"+b)
					b2 = usedcolors.index("-"+b)
					c1 = usedcolors.index("+"+c)
					c2 = usedcolors.index("-"+c)
					if b1 > b2: b1,b2 = b2,b1
					if c1 > c2: c1,c2 = c2,c1
					if b1 > c1: b1,b2,c1,c2 = c1,c2,b1,b2
					
					# Krupp is b1,c1,b2,c2
					is_Krupp = c1 < b2 and b2 < c2
					assert(is_Krupp or b2 < c1 or b2 > c2)

					if is_Krupp and nonKruppOnly: return False
					if not is_Krupp and kruppOnly: return False
				
				except ValueError:
					None # not yet in list

	return True
			

def enum_relevant_cycles(vertices,arcs,digonfree=False,kruppOnly=False,nonKruppOnly=False,firstIsDigon=False,insertGroundingLine=False):
	successors = {a:[b for b in vertices if (a,b) in arcs] for a in vertices}
	predecessor = {a:[b for b in vertices if (b,a) in arcs] for a in vertices}
	colors = list(set(arcs.values()))
	n = len(colors)

	if False:
		degs = compute_degrees(vertices,arcs)
		maxdeg = max(degs.values())
	
	for v0 in vertices:
		for c in enum_relevant_cycles_inner(n,vertices,arcs,successors,predecessor,[v0],[],digonfree=digonfree,kruppOnly=kruppOnly,nonKruppOnly=nonKruppOnly,firstIsDigon=firstIsDigon,insertGroundingLine=insertGroundingLine):
			yield c


# NOTE: usedcolors is currently a list of strings... could be optimized
def enum_relevant_cycles_inner(n,vertices,arcs,successors,predecessor,visited,usedcolors,digonfree=False,kruppOnly=False,nonKruppOnly=False,firstIsDigon=False,insertGroundingLine=False):
	l = len(visited)
	lexminorder = True

	if (kruppOnly or nonKruppOnly) and not validTriples(n,usedcolors,kruppOnly,nonKruppOnly,checklastonly=1):
		return
		
	if digonfree and l >= 3:
		if visited[l-1] == visited[l-3]:
			return
		if l == 2*n and visited[0] == visited[-2]:
			return

	if firstIsDigon:
		lexminorder = False
		if l == 3 and visited[-1] != visited[-3]:
			return

	if insertGroundingLine:
		if len(visited) >= 4 and visited[-1] != visited[-3] and visited[-2] != visited[-4]:
			return

	if l < 2*n:
		v = visited[-1]
		for w in predecessor[v]:
			if lexminorder and w < visited[0]: continue
			col = "-"+arcs[(w,v)]
			if col in usedcolors: continue
			for c in enum_relevant_cycles_inner(n,vertices,arcs,successors,predecessor,visited+[w],usedcolors+[col],digonfree=digonfree,kruppOnly=kruppOnly,nonKruppOnly=nonKruppOnly,firstIsDigon=firstIsDigon,insertGroundingLine=insertGroundingLine):
				yield c
		for w in successors[v]:
			if lexminorder and w < visited[0]: continue
			col = "+"+arcs[(v,w)]
			if col in usedcolors: continue
			for c in enum_relevant_cycles_inner(n,vertices,arcs,successors,predecessor,visited+[w],usedcolors+[col],digonfree=digonfree,kruppOnly=kruppOnly,nonKruppOnly=nonKruppOnly,firstIsDigon=firstIsDigon,insertGroundingLine=insertGroundingLine):
				yield c
	else:
		# test if cycle
		v = visited[-1]
		w = visited[0]
		if w in predecessor[v]:
			col = "-"+arcs[(w,v)]
		elif w in successors[v]:
			col = "+"+arcs[(v,w)]
		else:
			return # invalid

		# if valid, in any case ...
		if col not in usedcolors:
			usedcolors = usedcolors+[col]
			#print "usedcols",usedcolors
			assert(len(usedcolors) == 2*n)
			#for c in colors:
			#	assert(('+'+c) in usedcolors and ('-'+c) in usedcolors)

			if (kruppOnly or nonKruppOnly) and not validTriples(n,usedcolors,kruppOnly,nonKruppOnly,checklastonly=1): 
				return

			if not lexminorder or visited <= normalize_cyclic_list(visited):# and visited <= normalize_cyclic_list(list(reversed(visited))):
				yield visited,usedcolors


def calc_rotation_system(vertices,arcs,arcs_with_color):
	successors = {a:[b for b in vertices if (a,b) in arcs] for a in vertices}
	predecessor = {a:[b for b in vertices if (b,a) in arcs] for a in vertices}
	
	rs = {}
	for x in vertices:
		i0 = (predecessor[x]+successors[x])[0] # pick start vertex adjacent to x
		rs[x] = [i0]
		while True:
			y = rs[x][-1]
			if y in successors[x]:
				cur_arc = (x,y)
				assert(cur_arc in arcs)
				c = arcs[cur_arc]
				nxt_arc = arcs_with_color[c][(arcs_with_color[c].index(cur_arc)+1) % len(arcs_with_color[c])]
				nxt_y = nxt_arc[0]
			else:
				assert(y in predecessor[x])
				cur_arc = (y,x)
				assert(cur_arc in arcs)
				c = arcs[cur_arc]
				nxt_arc = arcs_with_color[c][arcs_with_color[c].index(cur_arc)-1]
				nxt_y = nxt_arc[1]
			if nxt_y == i0: 
				break # rotation system complete
			else:
				rs[x].append(nxt_y)
	return rs


def cycle_has_intersections(cycle,vertices,rotsys):
	l = len(cycle)
	for i in range(l):
		u = cycle[i-1]
		v = cycle[i]
		w = cycle[(i+1) % l]
		if u !=  w:
			ui = rotsys[v].index(u)
			wi = rotsys[v].index(w)
			for zi in cyclic_range(ui+1,wi,len(rotsys[v])):
				if rotsys[v][zi] in cycle:
					return True
	return False


def cyclic_range(a,b,c):
	a = a % c
	b = b % c
	while a != b:
		yield a % c
		a = (a+1) % c


def compute_degrees(vertices,arcs):
	deg = {v:0 for v in vertices}
	for (u,v) in arcs:
		deg[u] += 1
		deg[v] += 1
	return deg


def connected_components(vertices,edges):
	for v in vertices:
		assert(type(v) == int)
	
	predecessor = {v:v for v in vertices}
	def find_predecessor_(v):
		while True:
			c = predecessor[v]
			if c == v: 
				return c
			else:
				v = c

	for (u,v) in edges:
		pu = find_predecessor_(u)
		pv = find_predecessor_(v)
		if pu != pv:
			if pu > pv:
				predecessor[pu] = pv
			else:
				predecessor[pv] = pu

	component = {v:find_predecessor_(v) for v in vertices}
	component_inverse = [[v for v in vertices if component[v] == c] for c in set(component.values())]
	return component,component_inverse


class WrongDirectionException(Exception):
	def __init__(self):
		None
	def __str__(self):
		return repr(self.value)


DEBUG = False
# snow-plow algorithm
def extend_graph(cycle,vertices,arcs,rotsys):

	#if cycle[-1] == cycle[1]:
	#	cycle = cycle[1:]+cycle[:1]
	#	assert(cycle[-1]!=cycle[1])

	if DEBUG: print ("--------------- START EXTENDING ---------------------------")
	if DEBUG: print ("cycle",cycle)
	l = len(cycle)
	cycle_arcs = [[cycle[i-1],cycle[i]] for i in range(l)]

	vertices2 = copy(vertices)
	rotsys2 = {v:copy(rotsys[v]) for v in rotsys}

	arcs2 = copy(arcs)
	u_copy = None

	remaining_arcs = []
	if DEBUG: print ("arcs2",list(sorted([(min(e),max(e)) for e in arcs2.keys()])))

	for i in range(l):
		u = cycle[(i-1)%l]
		v = cycle[i]
		w = cycle[(i+1)%l]

		ui = rotsys2[v].index(u)
		wi = rotsys2[v].index(w)

		v_copy = len(vertices2)
		vertices2.append(v_copy)
		remaining_arcs.append((v,v_copy))

		if DEBUG: print ("do",i,": split",v,"->",v_copy)
		if DEBUG: print ("arcs2",arcs2.keys())
		if DEBUG: print ("cycle",cycle,cycle_arcs)

		for zi in cyclic_range(ui+1,wi,len(rotsys2[v])):
			z = rotsys2[v][zi]

			if (v,z) in arcs2:
				arcs2[(v_copy,z)] = arcs2[(v,z)]
				del arcs2[(v,z)]
				if z in vertices:
					rotsys2[z][rotsys2[z].index(v)] = v_copy
					# claim: we do not need a rotation system for the new vertices
					# so we just update the entry just for z (a original vertex)

				if DEBUG: print (v,z),"->",(v_copy,z)

				if (v,z) in cycle_arcs:
					xx = cycle_arcs.index((v,z))
					assert(cycle[(xx-1) % l] == v)
					assert(cycle_arcs[xx][0] == v)
					assert(cycle_arcs[(xx-1)%l][1] == v)
					cycle[xx] = v_copy
					cycle_arcs[xx][0] = v_copy
					cycle_arcs[(xx-1)%l][1] = v_copy
					if DEBUG: print ("UPDATED cycle",cycle,cycle_arcs)

			if (z,v) in arcs2:
				arcs2[(z,v_copy)] = arcs2[(z,v)]
				del arcs2[(z,v)]
				if z in vertices:
					rotsys2[z][rotsys2[z].index(v)] = v_copy
					# claim: we do not need a rotation system for the new vertices
					# so we just update the entry just for z (a original vertex)
				if DEBUG: print (z,v),"->",(z,v_copy)

				if (z,v) in cycle_arcs:
					xx = cycle_arcs.index((z,v))
					assert(cycle[xx] == v)		
					assert(cycle_arcs[xx][1] == v)
					assert(cycle_arcs[(xx+1)%l][0] == v)
					cycle[xx] = v_copy
					cycle_arcs[xx][1] = v_copy
					cycle_arcs[(xx+1)%l][0] = v_copy
					if DEBUG: print ("UPDATED cycle",cycle,cycle_arcs)
		
		if DEBUG: print ("cycle",cycle,cycle_arcs)
		if DEBUG: print ("arcs2",arcs2.keys())
		if DEBUG: print ()

		if i == 0:
			first_copy = v_copy
		else:
			assert(u_copy)
			assert((u,v) in arcs2 or (v,u) in arcs2)
			if (u,v) in arcs2:
				arcs2[(u_copy,v_copy)] = arcs2[(u,v)]
			if (v,u) in arcs2:
				arcs2[(v_copy,u_copy)] = arcs2[(v,u)]

		if DEBUG: print (arcs2)
		if DEBUG: print()
		u_copy = v_copy


	# close cycle
	u = cycle[-1]
	v = cycle[0]
	v_copy = first_copy

	assert((u,v) in arcs2 or (v,u) in arcs)
	if (u,v) in arcs2:
		arcs2[(u_copy,v_copy)] = arcs2[(u,v)]
	if (v,u) in arcs2:
		arcs2[(v_copy,u_copy)] = arcs2[(v,u)]
	
	if DEBUG: print ("arcs2_rem=",list(sorted([(min(e),max(e)) for e in arcs2.keys()+remaining_arcs])))

	return extend_graph_add_remaining_arcs(vertices2,arcs2,remaining_arcs)



def extend_graph_add_remaining_arcs(vertices2,arcs2,remaining_arcs):
	component,component_inverse = connected_components(vertices2,arcs2)
	
	assert(component[0] == 0) # unbounded face always in first connected component

	assert(len(component_inverse) != 1)
	assert(len(component_inverse) == 2)

	# add missing arcs (find out orientation)
	colors = list(set(arcs2.values()))
	new_color = [c for c in IPE_COLORS if c not in colors][0]

	for (u,v) in remaining_arcs:
		assert(component[u] != component[v])
		if component[u] == 0:
			arcs2[(v,u)] = new_color 
		else:
			arcs2[(u,v)] = new_color

		if DEBUG: print ("added",u,v)

	return vertices2,arcs2,new_color
