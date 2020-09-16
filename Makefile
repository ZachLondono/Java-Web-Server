PORT = 1234

all:
	javac PartialHTTP1Server.java

run:
	java PartialHTTP1Server $(PORT)
