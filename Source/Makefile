PORT = 1234

all:
	javac PartialHTTP1Server.java -d ../Build

run:
	(cd ../Build; java PartialHTTP1Server $(PORT))

clean:
	ls ../Build | grep ".*\.class$$" | xargs -I {} rm ../Build/{}
