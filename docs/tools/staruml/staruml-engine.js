// Loads StarUML's own model engine (metadata-json) and draws diagrams with
// node-canvas 3 - so that a .mdj is checked, measured and rendered exactly
// as StarUML reads and draws it.
var path = require("path");

module.exports = function (metadataJsonDir) {
    var mdjson = require(path.resolve(metadataJsonDir, "metadata-json"));
    var Canvas = require(require.resolve("canvas", { paths: [path.resolve(metadataJsonDir)] }));

    function diagrams() {
        return mdjson.Repository.findAll(function (e) {
            return e.ownedViews !== undefined && /Diagram$/.test(e.getClassName());
        });
    }

    /** Draws `diagram` - which also computes every view's size - and returns it as a PNG buffer. */
    function draw(diagram) {
        var probe = new mdjson.Graphics.Canvas(Canvas.createCanvas(1, 1).getContext("2d"));
        var box = diagram.getBoundingBox(probe);
        box.expand(10);
        var image = Canvas.createCanvas(box.getWidth(), box.getHeight());
        var ctx = image.getContext("2d");
        ctx.fillStyle = "#ffffff";
        ctx.fillRect(0, 0, box.getWidth(), box.getHeight());
        var canvas = new mdjson.Graphics.Canvas(ctx);
        canvas.origin = new mdjson.Graphics.Point(-box.x1, -box.y1);
        canvas.zoomFactor = new mdjson.Graphics.ZoomFactor(1, 1);
        diagram.drawDiagram(canvas);
        return image.toBuffer("image/png");
    }

    return { mdjson: mdjson, diagrams: diagrams, draw: draw };
};
