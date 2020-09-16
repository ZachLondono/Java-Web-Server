PORT = 1234

all:
	javac PartialHTTP1Server.java -d ../Build

run:
	(cd ../Build; java PartialHTTP1Server $(PORT))

runBackground:
	(cd ../Build; { java PartialHTTP1Server $(PORT) & echo $$! > server.PID; } )

stop:
	(cd ../Build; kill `cat server.PID` && rm server.PID)

clean:
	ls ../Build | grep ".*\.class$$" | xargs -I {} rm ../Build/{}