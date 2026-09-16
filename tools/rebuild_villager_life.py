"""One-command import, lip analysis, animation export and contact verification."""
import argparse
from pathlib import Path
import subprocess
import sys

def main():
    parser=argparse.ArgumentParser();parser.add_argument('source',type=Path);args=parser.parse_args()
    root=Path(__file__).resolve().parents[1]
    for script,arguments in [('import_villager_voices.py',[str(args.source.resolve())]),
                              ('generate_villager_life_assets.py',[]),
                              ('generate_villager_lip_sync.py',[]),
                              ('verify_villager_animation_reuse.py',[]),
                              ('verify_villager_life_motion.py',[]),('verify_villager_life_props.py',[])]:
        subprocess.run([sys.executable,str(root/'tools'/script),*arguments],cwd=root,check=True)

if __name__=='__main__':main()
