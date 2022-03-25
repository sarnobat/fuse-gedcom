# Install Macfuse: https://github.com/osxfuse/osxfuse/releases/download/macfuse-4.0.5/macfuse-4.0.5.dmg

##
## 2022-03
##

# Gedcom
/usr/local/Cellar/openjdk@11/11.0.12/bin/java -classpath /Volumes/git/github/groovy_libraries/.groovy/lib/guava-19.0.jar:/Volumes/git/github/groovy_libraries/.groovy/lib/fuse-jna-0.0.1-SNAPSHOT.jar:/Volumes/git/github/groovy_libraries/.groovy/lib/jna-3.4.0.jar -Dgedcom=$HOME/sarnobat.git/2021/genealogy/rohidekar.ged -Ddir=/tmp/gedcom FuseGedcom.java

# Errands.txt
/usr/local/Cellar/openjdk@11/11.0.12/bin/java -classpath /Volumes/git/github/groovy_libraries/.groovy/lib/guava-19.0.jar:/Volumes/git/github/groovy_libraries/.groovy/lib/fuse-jna-0.0.1-SNAPSHOT.jar:/Volumes/git/github/groovy_libraries/.groovy/lib/jna-3.4.0.jar -Ddir=/tmp/errands FuseErrandsTxt.java /tmp/1/
