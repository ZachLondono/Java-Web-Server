PORT = 1234

all:
	javac HTTP1Server.java -d ../Build

run:
	(cd ../Build; java HTTP1Server $(PORT))

runBackground:
	(cd ../Build; { java HTTP1Server $(PORT) & echo $$! > server.PID; } )

stop:
	(cd ../Build; kill `cat server.PID` && rm server.PID)

clean:
	ls ../Build | grep ".*\.class$$" | xargs -I {} rm ../Build/{}
