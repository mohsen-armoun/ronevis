AM_CPPFLAGS = -I$(top_srcdir)/src
lib_LTLIBRARIES = libwebpdemux.la

libwebpdemux_la_SOURCES =
libwebpdemux_la_SOURCES += demux.c

libwebpdemuxinclude_HEADERS =
libwebpdemuxinclude_HEADERS += ../webp/demux.h
libwebpdemuxinclude_HEADERS += ../webp/mux_types.h
libwebpdemuxinclude_HEADERS += ../webp/types.h

libwebpdemux_la_LIBADD = ../libwebp.la
libwebpdemux_la_LDFLAGS = -no-undefined -version-info 1:0:0
libwebpdemuxincludedir = $(includedir)/webp
pkgconfig_DATA = libwebpdemux.pc
