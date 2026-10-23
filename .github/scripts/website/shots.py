"""Screenshots of the real WordPress + Elementor pages (and the Elementor editor) for the website test."""
import asyncio, json, sys
from playwright.async_api import async_playwright
BASE = sys.argv[1]; PAGES = json.loads(sys.argv[2]); OUT = "out"
async def main():
    async with async_playwright() as p:
        b = await p.chromium.launch()
        errs = {}
        async def shot(url, name, w, h, full=True, reveal=True, scroll=0):
            pg = await b.new_page(viewport={"width": w, "height": h})
            e = []; pg.on("pageerror", lambda x: e.append(str(x))); pg.on("console", lambda m: e.append(m.text) if m.type == "error" else None)
            await pg.goto(url, wait_until="networkidle", timeout=90000)
            if reveal:
                # scroll through once so lazy pictures load and animations play, then back to the top
                hgt = await pg.evaluate("document.body.scrollHeight")
                for y in range(0, hgt, 700):
                    await pg.evaluate(f"window.scrollTo(0,{y})"); await pg.wait_for_timeout(120)
                await pg.evaluate("document.querySelectorAll('.acs-gallery__track').forEach(t=>{t.scrollLeft=9999;t.scrollLeft=0})")
                await pg.evaluate(f"window.scrollTo(0,{scroll})"); await pg.wait_for_timeout(1500)
            await pg.mouse.move(w * 0.6, h * 0.42); await pg.wait_for_timeout(500)
            await pg.screenshot(path=f"{OUT}/{name}.png", full_page=full)
            errs[name] = e; await pg.close()
        await shot(PAGES["home"], "home-desktop-top", 1440, 900, full=False)
        await shot(PAGES["home"], "home-desktop-full", 1440, 900)
        await shot(PAGES["home"], "home-phone-full", 390, 844)
        for k in ("capture", "photo", "video"):
            await shot(PAGES[k], f"{k}-features-desktop", 1440, 900, full=False)
        await shot(PAGES["photo"], "photo-features-phone", 390, 844, full=False)
        # Elementor editor: log in and open the home page in the editor
        try:
            pg = await b.new_page(viewport={"width": 1600, "height": 1000})
            await pg.goto(BASE + "/wp-login.php"); await pg.fill("#user_login", "admin"); await pg.fill("#user_pass", "admin"); await pg.click("#wp-submit")
            await pg.wait_for_load_state("networkidle")
            await pg.goto(BASE + f"/wp-admin/post.php?post={PAGES['home_id']}&action=elementor", timeout=120000)
            await pg.wait_for_timeout(20000)
            await pg.screenshot(path=f"{OUT}/elementor-editor.png")
            await pg.goto(BASE + "/wp-admin/options-general.php?page=alcorcut-site"); await pg.wait_for_timeout(1500)
            await pg.screenshot(path=f"{OUT}/settings-page.png", full_page=True)
        except Exception as ex:
            errs["editor"] = [repr(ex)]
        json.dump(errs, open(f"{OUT}/browser-errors.json", "w"), indent=1)
        await b.close()
asyncio.run(main())
