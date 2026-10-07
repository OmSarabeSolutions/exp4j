// Loads a .mdj with StarUML's engine and renders every diagram to PNG.
//   node render.js <metadata-json dir> <file.mdj> <out dir>
// Exits with an error if the file can't be loaded or a diagram can't be drawn.
var path = require("path"), fs = require("fs");
var engine = require("./staruml-engine")(process.argv[2]);
var root = engine.mdjson.loadFromFile(process.argv[3]);
var out = process.argv[4], failed = 0;
fs.mkdirSync(out, { recursive: true });
engine.diagrams().forEach(function (d) {
    var name = d.name.normalize("NFD").replace(/[̀-ͯ]/g, "").replace(/[^\w.-]+/g, "_").replace(/_+/g, "_");
    try {
        fs.writeFileSync(path.join(out, name + ".png"), engine.draw(d));
        console.log("OK  ", d.getClassName(), "-", d.name);
    } catch (e) {
        failed++;
        console.log("FAIL", d.getClassName(), "-", d.name, e.stack.split("\n").slice(0, 4).join(" | "));
    }
});
if (!root || failed) { process.exit(1); }
