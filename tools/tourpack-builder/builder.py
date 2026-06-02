#!/usr/bin/env python3
"""Build a small public-source TrailSage tour pack without API keys."""
import argparse, json, pathlib, time, urllib.parse, urllib.request, zipfile

USER_AGENT = "TrailSageTourPackBuilder/0.1 (public-source offline tour builder)"

def fetch_json(url, cache):
    cache.mkdir(parents=True, exist_ok=True)
    path = cache / (str(abs(hash(url))) + ".json")
    if path.exists(): return json.loads(path.read_text(encoding="utf-8"))
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=20) as response:
        data = json.load(response)
    path.write_text(json.dumps(data, indent=2), encoding="utf-8")
    time.sleep(0.5)
    return data

def main():
    p = argparse.ArgumentParser()
    p.add_argument("destination"); p.add_argument("--bbox", required=True)
    p.add_argument("--route"); p.add_argument("--osm"); p.add_argument("--out", default="tourpack-output")
    args = p.parse_args(); out = pathlib.Path(args.out); out.mkdir(parents=True, exist_ok=True)
    api = "https://en.wikipedia.org/w/api.php?" + urllib.parse.urlencode({"action":"query","format":"json","prop":"extracts|info","inprop":"url","exintro":1,"explaintext":1,"titles":args.destination})
    data = fetch_json(api, pathlib.Path("tourpack-cache"))
    page = next(iter(data["query"]["pages"].values())); source_id = "wikipedia-main"
    extract = page.get("extract", "")
    (out/"manifest.json").write_text(json.dumps({"id":args.destination.lower().replace(" ","-"),"name":args.destination,"bbox":args.bbox}, indent=2))
    (out/"sources.json").write_text(json.dumps([{"id":source_id,"title":page.get("title"),"url":page.get("fullurl"),"license":"CC BY-SA"}], indent=2))
    (out/"rag_chunks.json").write_text(json.dumps([{"id":"main","text":extract,"sourceIds":[source_id]}], indent=2))
    for name, value in {"pois.geojson":{"type":"FeatureCollection","features":[]},"route.geojson":{"type":"Feature","properties":{},"geometry":{"type":"LineString","coordinates":[]}},"triggers.json":[],"stories.json":[],"attribution.json":{"content":["Wikipedia contributors (CC BY-SA)"],"map":["OpenStreetMap contributors (ODbL)"]}}.items():
        (out/name).write_text(json.dumps(value, indent=2))
    with zipfile.ZipFile(str(out)+".zip", "w", zipfile.ZIP_DEFLATED) as z:
        for f in out.iterdir(): z.write(f, f.name)
    print(f"Built {out}.zip. Add user-supplied OSM extract and POI review for production.")
if __name__ == "__main__": main()

