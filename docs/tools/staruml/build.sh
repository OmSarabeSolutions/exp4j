#!/bin/zsh
# Updates docs/uml/exp4j.mdj from the sources - keeping what was
# done by hand in StarUML - and re-renders docs/uml/images/*.png.
# Run from the exp4j project root:   docs/tools/staruml/build.sh
#
# Needs python3 and Graphviz (dot). For the exact sizes and the images, also
# git, Node.js and npm: StarUML's engine (metadata-json) is fetched once into
# $STARUML_ENGINE (default ~/.cache/function-plotter/metadata-json).
set -e
TOOLS=docs/tools/staruml
MODEL=docs/uml/exp4j.mdj
ENGINE=${STARUML_ENGINE:-$HOME/.cache/function-plotter/metadata-json}
WORK=$(mktemp -d -t exp4j-uml)
trap 'rm -rf "$WORK"' EXIT

if [[ ! -d $ENGINE ]]; then
  git clone --depth 1 https://github.com/staruml/metadata-json.git "$ENGINE"
  (cd "$ENGINE" && npm install --no-save fs-extra@0.12.0 ejs@1.0.0 markdown@0.5.0 escape-html@1.0.1 canvas@3 \
     && node -e "require('canvas')" 2>/dev/null || npm rebuild canvas)
fi

[[ -f $MODEL ]] && cp $MODEL "$WORK/before.mdj"
# 1. A trial merge, to measure the views as StarUML draws them
python3 $TOOLS/generate_staruml.py --base $MODEL --out "$WORK/trial.mdj" > /dev/null
node $TOOLS/measure.js "$ENGINE" "$WORK/trial.mdj" "$WORK/sizes.json"
# 2. The real merge, into the model, with the measured sizes
python3 $TOOLS/generate_staruml.py --sizes "$WORK/sizes.json"
[[ -f $WORK/before.mdj ]] && (cd $TOOLS && python3 check_preserved.py "$WORK/before.mdj" "../../../$MODEL")
rm -rf docs/uml/images
node $TOOLS/render.js "$ENGINE" $MODEL docs/uml/images
