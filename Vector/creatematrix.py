# importing libraries necessary for clustering
import numpy #arrays, hstack, vstack
import scipy.cluster.hierarchy #all the clustering tools (linkages, trees, clusternodes)
import scipy.spatial.distance # pdst - condenses matrix produced by creatematrix

numlines = 0 # helps skip over the first line which usually has some other numbers. numlines is global because it also defines the size of the path array

#recursive function to find the path to a word 
def recursfindnodepath(d,path): 
	if d.is_leaf() == False: # we only want branch nodes as 1. leaf nodes are the end of the line and 2. asking for the left branch of a leaf is impossible
		recursfindnodepath(d.get_left(),path+"0") #this calls the function again but now refers to the left branch and tacks a 0 onto the path to indicate leftward movement on the tree
		recursfindnodepath(d.get_right(),path+"1") #this calls the function again but now refers to the right branch and tacks a 1 onto the path to indicate rightward movement on the tree
	elif d.is_leaf():
		patharray[d.get_id()] = path #this means that we have reached a leaf. we take the id for the leaf, it's index, 

# checks whether a string is a number (a float specifically)
def is_number(s):
    try:
        float(s)
        return True
    except ValueError:
        return False
def outputmatrix(wordpath):
	file = open('germanwordpath.txt','w')
	for x in wordpath:
		file.write(x[0]+" "+x[1]+"\n")
	file.close()

# takes a word vector text file input and gives a hierarchal clustering
def creatematrix(filename): 
	global numlines
	global wordarray
	with open (filename,'r') as f:
		languagearray = numpy.empty((0,50),float) # main array -- rows: words(words themselves not included), columnns: observations. Array is empty so it can merge with languageline for the first time
		totalchecked = 0
		for line in f:
			if numlines != 0: # skips over the first line which (at least in basque and german) has some other number
				print("Hi, I'm on line: "+str(numlines)) #check for progress
				languageline = numpy.array(()) # array to store data from each line
				numbers = "" # exists for debugging purposes
				numberofnumbers = 0 # exists for debugging purposes
				numwords = 0
				for word in line.split():
					if numwords != 0: # first string value is the word itself, which we want to exclude
						if is_number(word): # this is pretty unnecessary now but I'll keep it since it doesn't harm the program
							numbers += word + " " 
							numberofnumbers +=1
							languageline = numpy.append(languageline,float(word)) # adds the observation to the array for this line
					else:
						wordarray = numpy.append(wordarray,word)
					numwords += 1
				if numberofnumbers == 50: # makes sure that the line had actual observations (200 as that is how many word2vec specified) and is not a blank line
					languagearray = numpy.vstack((languagearray,languageline)) # appends observations from the line to the larger array by merging languageline and languagearray
					totalchecked+=1
			if totalchecked == 500: # check to make sure that the program works as the full program has hundreds of thousands of lines
				return languagearray
			numlines +=1
wordarray = numpy.array(())
condensed_language_matrix = scipy.spatial.distance.pdist(creatematrix('germanwordvector.txt'))# creates a condensed distance matrix using array frrom the create function
parraysize = "S"+str(numlines)
patharray = numpy.empty(numlines,dtype=parraysize)	
language_linkage = scipy.cluster.hierarchy.linkage(condensed_language_matrix,metric="euclidean",method="complete") # performs hierarchal/agglomerative clustering on the condensed matrix
language_tree = scipy.cluster.hierarchy.to_tree(language_linkage) #converts the language linkage object into an easy to use tree object
recursfindnodepath(language_tree,"") #this starts the function off with the topmost branch
outputmatrix(zip(wordarray,patharray))
