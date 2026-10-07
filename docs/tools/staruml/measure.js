// Draws every diagram of a .mdj, as StarUML does when it opens it, and writes
// the size each node view ends up with: {viewId: [width, height]}.
//   node measure.js <metadata-json dir> <file.mdj> <sizes.json>
var engine = require("./staruml-engine")(process.argv[2]);
engine.mdjson.loadFromFile(process.argv[3]);
var sizes = {};
engine.diagrams().forEach(function (d) {
    engine.draw(d);
    d.ownedViews.forEach(function (v) {
        if (v instanceof engine.mdjson.Core.NodeView) { sizes[v._id] = [v.width, v.height]; }
    });
});
require("fs").writeFileSync(process.argv[4], JSON.stringify(sizes));
console.log(Object.keys(sizes).length + " node sizes measured");
