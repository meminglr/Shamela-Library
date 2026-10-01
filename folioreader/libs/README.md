# Vendored NanoHttpd

`r2-streamer-kotlin` (the EPUB server used by the reader) depends on NanoHttpd commit
`fd80618e93`, which JitPack no longer serves. These jars are built from that commit with
`nanohttpd-loopback.patch` applied (the server binds to 127.0.0.1 instead of all interfaces).

Rebuild:

```bash
git clone https://github.com/NanoHttpd/nanohttpd.git && cd nanohttpd
git checkout fd80618e93 && git apply ../nanohttpd-loopback.patch
mkdir -p out/core out/nl
javac --release 8 -d out/core $(find core/src/main/java -name "*.java")
cp -r core/src/main/resources/* out/core/
javac --release 8 -cp out/core -d out/nl $(find nanolets/src/main/java -name "*.java")
jar cf nanohttpd-fd80618e93.jar -C out/core . && jar cf nanohttpd-nanolets-fd80618e93.jar -C out/nl .
```
