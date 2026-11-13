"""Generate app/src/main/java/.../ui/theme/HugeIcons.kt from the free Hugeicons npm package (MIT).

Usage:
  mkdir -p /tmp/huge && cd /tmp/huge && npm pack @hugeicons/core-free-icons && tar xzf hugeicons-core-free-icons-*.tgz
  python3 -I tools/gen_hugeicons.py /tmp/huge/package/dist/esm app/src/main/java/com/yourteam/expensetracker/ui/theme/HugeIcons.kt

To add an icon: add (KotlinName, HugeiconsFileName, what-it-is-for) to ICONS and rerun.
"""
import re, sys
src, out = sys.argv[1], sys.argv[2]
ICONS = [  # (kotlin name, hugeicons file, what it's for)
 ("Home","Home01","Home tab"),("Chart","ChartHistogram","Insights tab"),("Wallet","Wallet01","Budgets tab"),
 ("Scan","Scan","Scan & Pay"),("TaskDone","TaskDone01","Review tab / To check"),("Sparkles","Sparkles","app/AI picked a category"),
 ("Refresh","Refresh","refresh insights"),("Plug","Plug01","sources"),
 ("Restaurant","Restaurant01","Food & Dining"),("Car","Car01","Travel"),("ShoppingBag","ShoppingBag01","Shopping"),
 ("ShoppingBasket","ShoppingBasket01","Groceries"),("Invoice","Invoice01","Bills"),("Medicine","MedicineBottle01","Health"),
 ("Ticket","Ticket01","Entertainment"),("Mortarboard","Mortarboard01","Education"),("Perfume","Perfume","Personal care"),
 ("Building","Building03","Rent"),("MoreCircle","MoreHorizontalCircle01","Others"),("Briefcase","Briefcase01","Salary"),
 ("ReturnRequest","ReturnRequest","Refund"),("PercentCircle","PercentCircle","Interest"),("Coins","Coins01","Cashback"),
 ("Transfer","ArrowDataTransferHorizontal","Transfers"),("MoneyAdd","MoneyAdd01","Other income"),("Tag","Tag01","custom / no category"),
 ("ChevronRight","ArrowRight01","› chevron"),("ChevronLeft","ArrowLeft01","‹ chevron"),("ChevronDown","ArrowDown01","⌄ chevron"),
 ("Close","Cancel01","close"),("Tick","Tick02","check mark"),("Add","Add01","add"),("Search","Search01","search"),
 ("ArrowBack","ArrowLeft02","back arrow"),("ArrowForward","ArrowRight02","forward arrow"),("Info","InformationCircle","info"),
 ("Alert","Alert02","warning"),("User","User","profile"),("CheckCircle","CheckmarkCircle02","done"),
]
def f(x): return float(x)
def num(v): return ("%.4f" % v).rstrip('0').rstrip('.')
def to_d(tag, a):
    if tag == "path": return a["d"]
    if tag in ("circle","ellipse"):
        cx, cy = f(a["cx"]), f(a["cy"])
        rx = f(a.get("rx", a.get("r"))); ry = f(a.get("ry", a.get("r")))
        return f"M{num(cx-rx)} {num(cy)}A{num(rx)} {num(ry)} 0 1 0 {num(cx+rx)} {num(cy)}A{num(rx)} {num(ry)} 0 1 0 {num(cx-rx)} {num(cy)}Z"
    if tag == "rect":
        x, y, w, h = f(a.get("x","0")), f(a.get("y","0")), f(a["width"]), f(a["height"])
        r = f(a.get("rx", a.get("ry", "0")))
        if r == 0: return f"M{num(x)} {num(y)}H{num(x+w)}V{num(y+h)}H{num(x)}Z"
        return (f"M{num(x+r)} {num(y)}H{num(x+w-r)}A{num(r)} {num(r)} 0 0 1 {num(x+w)} {num(y+r)}V{num(y+h-r)}"
                f"A{num(r)} {num(r)} 0 0 1 {num(x+w-r)} {num(y+h)}H{num(x+r)}A{num(r)} {num(r)} 0 0 1 {num(x)} {num(y+h-r)}"
                f"V{num(y+r)}A{num(r)} {num(r)} 0 0 1 {num(x+r)} {num(y)}Z")
    raise SystemExit("unsupported " + tag)
lines = ["// Generated from @hugeicons/core-free-icons 4.4.0 (MIT, Copyright (c) 2025 Hugeicons).",
 "// Stroke Rounded, 24 x 24. Regenerate rather than hand-edit.",
 "package com.yourteam.expensetracker.ui.theme","",
 "import androidx.compose.ui.graphics.vector.ImageVector","",
 "/** Hugeicons (free, MIT) used across the app. Tint with the Icon composable; never hard-code a colour. */",
 "object HugeIcons {"]
for kt, file, why in ICONS:
    js = open(f"{src}/{file}Icon.js").read()
    parts = []
    for tag, body in re.findall(r'\["(\w+)", \{(.*?)\}\]', js):
        a = dict(re.findall(r'(\w+): "([^"]*)"', body))
        d = to_d(tag, a)
        stroke = a.get("stroke") == "currentColor"
        fill = a.get("fill") == "currentColor"
        w = a.get("strokeWidth", "1.5")
        round_ = a.get("strokeLinecap") == "round"
        evenodd = a.get("fillRule") == "evenodd"
        parts.append(f'        HugePart("{d}", stroke = {str(stroke).lower()}, fill = {str(fill).lower()}, width = {w}f, round = {str(round_).lower()}' + (", evenOdd = true" if evenodd else "") + ")")
    lines.append(f"    /** {why} ({file}). */")
    lines.append(f'    val {kt}: ImageVector by lazy {{ hugeIcon("{kt}",')
    lines.append(",\n".join(parts))
    lines.append("    ) }")
lines.append("}")
open(out, "w").write("\n".join(lines) + "\n")
print(len(ICONS), "icons")
