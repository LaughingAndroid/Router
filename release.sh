## annotation
#./gradlew :annotation:clean :annotation:pBPTML
#./gradlew :annotation:bintrayUpload
#
## compiler
#./gradlew :compiler:clean :compiler:pBPTML
#./gradlew :compiler:bintrayUpload

# router
./gradlew :router:aR
./gradlew  bintrayUpload -PbintrayUser=$1 -PbintrayKey=$2 -PdryRun=false -x releaseAndroidJavadocs