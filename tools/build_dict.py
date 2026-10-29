"""Build the compact offline dictionary asset (app/src/main/assets/dict.db) from ECDICT.

Run from the repository root:
  1. bash tools/parallel_dl.sh      # places ecdict-sqlite-28.zip in the repo root
  2. python tools/build_dict.py     # writes app/src/main/assets/dict.db + build/stardict-full.db

ECDICT is MIT-licensed (github.com/skywind3000/ECDICT); attribution is kept in the app's
about screen and README.
"""
import os
import sqlite3
import zipfile

MAX_RANK = 50_000
SRC_ZIP = "ecdict-sqlite-28.zip"
WORK_DB = os.path.join("build", "stardict-full.db")
OUT_DB = os.path.join("app", "src", "main", "assets", "dict.db")


def extract_source():
    if os.path.exists(WORK_DB) and os.path.getsize(WORK_DB) > 100 * 1024 * 1024:
        print("source db already extracted")
        return
    print("unzipping (this takes a while)...")
    with zipfile.ZipFile(SRC_ZIP) as zf:
        target = next(n for n in zf.namelist() if n.lower().endswith(".db"))
        # Zip Slip guard: only flat entries at the archive root are accepted.
        if os.path.basename(target) != target or target.startswith(".") or ".." in target:
            raise SystemExit(f"refusing unsafe zip entry: {target}")
        print("entry:", target)
        # zipfile.extract sanitises member paths (strips separators and ".." components).
        os.makedirs("build", exist_ok=True)
        zf.extract(target, "build")
        if WORK_DB != os.path.join("build", target):
            os.replace(os.path.join("build", target), WORK_DB)
    print("extracted:", os.path.getsize(WORK_DB) / 1024 / 1024, "MB")


def main():
    if not os.path.exists(SRC_ZIP):
        raise SystemExit(f"missing {SRC_ZIP} in the repository root; run tools/parallel_dl.sh first")
    extract_source()

    if os.path.exists(OUT_DB):
        os.remove(OUT_DB)
    os.makedirs("app/src/main/assets", exist_ok=True)

    src = sqlite3.connect(WORK_DB)
    dst = sqlite3.connect(OUT_DB)
    dst.execute("PRAGMA journal_mode=OFF")
    dst.execute("PRAGMA synchronous=OFF")
    dst.execute(
        """
        CREATE TABLE dict(
            word TEXT PRIMARY KEY,
            phonetic TEXT,
            translation TEXT,
            definition TEXT,
            pos TEXT,
            collins INTEGER,
            oxford INTEGER,
            tag TEXT,
            frq INTEGER,
            bnc INTEGER,
            rank INTEGER
        )
        """
    )

    kept = 0
    for word, phonetic, translation, definition, pos, collins, oxford, tag, frq, bnc in src.execute(
        """
        SELECT word, phonetic, translation, definition, pos, collins, oxford, tag, frq, bnc
        FROM stardict
        WHERE (frq > 0 AND frq <= ?) OR (bnc > 0 AND bnc <= ?)
        """,
        (MAX_RANK, MAX_RANK),
    ):
        if not word or not word.strip():
            continue
        if not translation and not definition:
            continue
        ranks = [r for r in (frq, bnc) if r and r > 0]
        rank = min(ranks) if ranks else MAX_RANK + 1
        dst.execute(
            "INSERT OR REPLACE INTO dict VALUES(?,?,?,?,?,?,?,?,?,?,?)",
            (
                word.strip().lower(), phonetic, translation, definition, pos,
                collins or 0, oxford or 0, tag, frq or 0, bnc or 0, rank,
            ),
        )
        kept += 1
        if kept % 10_000 == 0:
            print("  kept", kept)

    dst.commit()
    dst.execute("CREATE INDEX idx_dict_rank ON dict(rank)")
    dst.commit()

    count = dst.execute("SELECT COUNT(*) FROM dict").fetchone()[0]
    freq = dst.execute("SELECT COUNT(*) FROM dict WHERE rank <= 12000").fetchone()[0]
    dst.execute("VACUUM")
    dst.close()
    src.close()

    size_mb = os.path.getsize(OUT_DB) / 1024 / 1024
    print(f"words kept: {count} (rank<=12k: {freq})")
    print(f"dict.db: {size_mb:.1f} MB -> {OUT_DB}")


if __name__ == "__main__":
    main()
