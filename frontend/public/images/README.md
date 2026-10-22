# Dish photos

Each dish shows the best picture available, in this order:

1. **Your own photo** in this folder, named after the dish's item id from
   `src/main/resources/data/catalog.json` (for example `i401.jpg` for Masala Dosa). Around 600×520 px works well.
2. **A Wikipedia photo.** `frontend/src/photos.js` maps each dish to a Wikipedia article, and the browser loads
   that article's lead image through Wikipedia's public API. These images are hosted on Wikimedia Commons under
   free licences (mostly Creative Commons); hovering a photo shows its source. Check an image's licence page before
   using it anywhere outside this demo.
3. **A drawn illustration** if neither exists or you're offline.

To change a dish's photo, edit its article name in `photos.js`, or drop your own file here.
