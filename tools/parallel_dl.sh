#!/bin/bash
# Parallel ranged download of a GitHub release asset (per-connection throttling on some
# networks makes a single stream very slow).
#
# Usage: bash tools/parallel_dl.sh [asset_api_url] [output_file]
# Default fetches the ECDICT sqlite release asset used by tools/build_dict.py.
set -u

ASSET_API="${1:-https://api.github.com/repos/skywind3000/ECDICT/releases/assets/7196549}"
OUT="${2:-ecdict-sqlite-28.zip}"
N=16

echo "[1/4] resolving final URL..."
FINAL=$(curl -sIL -o /dev/null -w '%{url_effective}' -H "Accept: application/octet-stream" "$ASSET_API")
echo "final: $FINAL"

TOTAL=$(curl -sI "$FINAL" | grep -i "content-length" | tail -1 | tr -dc '0-9')
if [ -z "$TOTAL" ] || [ "$TOTAL" -lt 1000000 ]; then
  echo "ERROR: cannot determine total size ($TOTAL)"; exit 1
fi
echo "[2/4] total bytes: $TOTAL"

CHUNK=$(( (TOTAL + N - 1) / N ))
rm -f part_*.bin
PIDS=()
for i in $(seq 0 $((N-1))); do
  START=$((i*CHUNK))
  END=$(( (i+1)*CHUNK - 1 ))
  [ "$END" -ge "$TOTAL" ] && END=$((TOTAL-1))
  NAME=$(printf "part_%02d.bin" "$i")
  curl -s --retry 3 --retry-delay 2 -r "$START-$END" -o "$NAME" "$FINAL" &
  PIDS+=($!)
done
echo "[3/4] waiting for $N segments..."
FAIL=0
for pid in "${PIDS[@]}"; do
  wait "$pid" || FAIL=1
done

cat $(printf "part_%02d.bin " $(seq 0 $((N-1)))) > "$OUT"
GOT=$(stat -c %s "$OUT" 2>/dev/null || echo 0)
echo "[4/4] got $GOT / $TOTAL"
if [ "$GOT" = "$TOTAL" ] && [ "$FAIL" -eq 0 ]; then
  rm -f part_*.bin
  echo "DOWNLOAD_OK"
else
  echo "DOWNLOAD_FAILED (size mismatch or segment error)"
  exit 1
fi
