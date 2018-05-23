all: mylib.so server

mylib.o: mylib.c
	gcc -I../include -L../lib -Wall -g -fPIC -DPIC -c mylib.c

mylib.so: mylib.o
	ld -shared -o mylib.so mylib.o -ldl

server: server.c
	gcc  -I../include -L../lib ../lib/libdirtree.so -g -pthread server.c -o server

clean:
	rm -f *.o *.so
	rm server