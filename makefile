all:
	cd Code && javac sender.java
	cd Code && javac receiver.java

clean:
	rm Code/*.class