#!/bin/bash
# Builds libraw.so (LibRaw, LGPL 2.1, unmodified) and libveyroraw.so (VelaCut Pro's bridge).
#   build.sh <LibRaw source dir> <out dir> <CC> <CXX> [extra flags]
# CC / CXX: an Android NDK clang (e.g. aarch64-linux-android26-clang) or the host gcc for tests.
set -euo pipefail
SRC=$1; OUT=$2; CC=$3; CXX=$4; EXTRA=${5:-}
HERE=$(cd "$(dirname "$0")" && pwd)
mkdir -p "$OUT/obj"
DEFS="-DLIBRAW_NOTHREADS -DNO_LCMS -DNO_JASPER -DNO_JPEG -DLIBRAW_BUILDLIB -fPIC -O2 -w $EXTRA"
objs=()
for f in $(find "$SRC/src" -name '*.cpp' ! -name '*_ph.cpp' | sort); do
  o="$OUT/obj/$(echo "${f#$SRC/src/}" | tr / _).o"
  objs+=("$o")
  [ -f "$o" ] || echo "$CXX $DEFS -I$SRC -c $f -o $o"
done | xargs -P "$(nproc)" -I{} sh -c '{}'
objs=$(ls "$OUT"/obj/*.o | grep -v '_ph\.cpp\.o')
$CXX -shared -o "$OUT/libraw.so" $objs -static-libstdc++ -Wl,-soname,libraw.so $EXTRA -lm
$CC -shared -fPIC -O2 $EXTRA -I"$SRC" ${JXL_INC:+-I$JXL_INC} ${JNI_INC:-} -o "$OUT/libveyroraw.so" "$HERE/veyroraw.c" -L"$OUT" -lraw -ldl -Wl,-rpath,'$ORIGIN' -Wl,-soname,libveyroraw.so
rm -rf "$OUT/obj"
ls -l "$OUT"
