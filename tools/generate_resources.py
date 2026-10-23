"""Deterministic, editable pixel art / recipes / sound-event mappings. Standard library only."""
from pathlib import Path
import json, struct, zlib

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources'
ASSETS = ROOT / 'assets/dimensioncollapse'

def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

def png(path, rows, colors):
    width, height = len(rows[0]), len(rows)
    assert all(len(row) == width for row in rows)
    raw = b''.join(b'\0' + b''.join(bytes(colors[c]) for c in row) for row in rows)
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b''))

palette = {'.':(0,0,0,0), 'o':(18,23,38,255), 'd':(43,52,71,255), 's':(83,101,126,255),
           'l':(163,191,210,255), 'w':(227,252,255,255), 'c':(89,230,240,255),
           'b':(36,120,166,255), 'p':(158,105,230,255), 'v':(73,44,120,255), 'g':(240,193,90,255)}
icons = {
    'dual_vector_foil': [
        '................','................','.........o......','.......oowo.....',
        '.....oowwco.....','...oowwccpo.....','..owwccppvo.....','.owccppvvvo.....',
        '.ocppvvvvo......','..opvvvvo.......','...ovvvo........','....ovo.........',
        '.....o..........','................','................','................'],
    'foil_launcher': [
        '................','................','..........oooo..','....ooooooscco..',
        '..oossllssbccwo.','.osdddssssbccwo.','.ospppddssbccwo.','.ospppddssbccwo.',
        '..ooddddooosso..','....oddo..ooo...','....oddo........','...osdo.........',
        '...osdo.........','...ooo..........','................','................'],
    'resource_transmitter': [
        '................','.....oooooo.....','....osllllso....','...osddddddso...',
        '...odoccccod o...'.replace(' ',''), '...odocwwcod o...'.replace(' ',''),
        '...odocwwcod o...'.replace(' ',''),'...odoccccod o...'.replace(' ',''),
        '...oddddddddo...','...odogogogdo...','...oddddddddo...','...odoppppdo....',
        '...osddddddso...','....osllllso....','.....oooooo.....','................']
}
# Keep the handheld transmitter silhouette symmetrical.
icons['resource_transmitter'] = [
    '................','.....oooooo.....','....osllllso....','...osddddddso...',
    '...odoccccodo...','...odocwwcodo...','...odocwwcodo...','...odoccccodo...',
    '...oddddddddo...','...odogogogdo...','...oddddddddo...','...odoppppodo...',
    '...osddddddso...','....osllllso....','.....oooooo.....','................']
palette['e'] = (112, 245, 172, 255)
icons['player_transmitter'] = [
    '................','.....oooooo.....','...oovppppvoo...','..ovpwwwwwwpvo..',
    '..opwdoooddwpo..','.ovwdoeeoodwvo..','.opwdoeeoodwpo..','.opwddoodddwpo..',
    '.opwdoeeeodwpo..','.opwdoeeeodwpo..','.ovwdodododwvo..','..opwdododwpo...',
    '..ovpwwwwwwpvo..','...oovppppvoo...','.....oooooo.....','................']
for name, rows in icons.items():
    png(ASSETS / f'textures/item/{name}.png', rows, palette)
    write_json(ASSETS / f'models/item/{name}.json', {'parent':'minecraft:item/handheld' if name == 'foil_launcher' else 'minecraft:item/generated', 'textures':{'layer0':f'dimensioncollapse:item/{name}'}})

for name, accent in [('expansion_suppressor','p'), ('expansion_suppressor_active','c'), ('transfer_receiver','c')]:
    for face in ['side','top']:
        rows = []
        for y in range(16):
            row = ''
            for x in range(16):
                c = 'd'
                if x in (0,15) or y in (0,15): c = 'o'
                elif x in (1,14) or y in (1,14): c = 's'
                elif x in (2,13) and y in (2,13): c = 'l'
                elif face == 'side':
                    if 4 <= x <= 11 and 4 <= y <= 10: c = 'o'
                    if 5 <= x <= 10 and y in (5,7,9): c = accent
                    if y == 12 and x in (5,8,10): c = 'g'
                else:
                    r = max(abs(x-7.5), abs(y-7.5))
                    if 3 <= r <= 4: c = accent
                    elif r < 3: c = 'o'
                    if r < 1: c = 'w'
                row += c
            rows.append(row)
        png(ASSETS / f'textures/block/{name}_{face}.png', rows, palette)
    write_json(ASSETS / f'models/block/{name}.json', {'parent':'minecraft:block/cube_bottom_top','textures':{'side':f'dimensioncollapse:block/{name}_side','top':f'dimensioncollapse:block/{name}_top','bottom':'minecraft:block/iron_block'}})

write_json(ASSETS/'blockstates/expansion_suppressor.json', {'variants':{'active=false':{'model':'dimensioncollapse:block/expansion_suppressor'},'active=true':{'model':'dimensioncollapse:block/expansion_suppressor_active'}}})
write_json(ASSETS/'blockstates/transfer_receiver.json', {'variants':{'':{'model':'dimensioncollapse:block/transfer_receiver'}}})
for name in ['expansion_suppressor','transfer_receiver']:
    write_json(ASSETS/f'models/item/{name}.json', {'parent':f'dimensioncollapse:block/{name}'})
    write_json(ROOT/f'data/dimensioncollapse/loot_table/blocks/{name}.json', {'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':f'dimensioncollapse:{name}'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})

# Reuse built-in sound events; no missing audio files or downloaded/licensed assets.
events = {
 'collapse.start':('entity.warden.sonic_boom',0.65), 'collapse.ambient':('block.portal.ambient',0.6),
 'collapse.crackle':('block.amethyst_block.break',0.7), 'entity.flatten':('block.enchantment_table.use',0.6),
 'plane.enter':('block.portal.travel',1.6), 'plane.leave':('block.beacon.deactivate',1.1),
 'launcher.charge':('item.crossbow.loading_start',0.7), 'launcher.ready':('block.note_block.chime',1.4),
 'launcher.fire':('entity.breeze.shoot',0.8), 'suppressor.activate':('block.beacon.activate',0.8),
 'suppressor.deactivate':('block.beacon.deactivate',0.8), 'suppressor.hum':('block.beacon.ambient',0.65),
 'suppressor.fuel':('block.amethyst_block.chime',1.2), 'transfer.send':('entity.enderman.teleport',0.8),
 'transfer.receive':('block.amethyst_block.resonate',1.1), 'transfer.link':('block.note_block.pling',1.4),
 'transfer.fail':('block.note_block.bass',0.6)
}
write_json(ASSETS/'sounds.json', {key:{'subtitle':f'subtitles.dimensioncollapse.{key}','sounds':[{'name':f'minecraft:{event}','type':'event','pitch':pitch}]} for key,(event,pitch) in events.items()})

def recipe(name, pattern, keys, count=1):
    write_json(ROOT/f'data/dimensioncollapse/recipe/{name}.json', {'type':'minecraft:crafting_shaped','category':'misc','pattern':pattern,'key':{k:{'item':v if ':' in v else 'minecraft:'+v} for k,v in keys.items()},'result':{'id':'dimensioncollapse:'+name,'count':count}})
recipe('dual_vector_foil',['APA','ENE','APA'],{'A':'amethyst_shard','P':'paper','E':'ender_pearl','N':'nether_star'})
recipe('foil_launcher',['ICI','EDE',' II'],{'I':'iron_ingot','C':'crossbow','E':'ender_pearl','D':'diamond'})
recipe('expansion_suppressor',['IAI','ERE','IOI'],{'I':'iron_ingot','A':'amethyst_shard','E':'ender_pearl','R':'redstone_block','O':'obsidian'})
recipe('transfer_receiver',['IEI','ECE','IRI'],{'I':'iron_ingot','E':'ender_pearl','C':'chest','R':'redstone'})
recipe('resource_transmitter',[' A ','ERE',' I '],{'A':'amethyst_shard','E':'ender_pearl','R':'redstone','I':'iron_ingot'})
recipe('player_transmitter',['AEA','DCD','ARA'],{'A':'amethyst_shard','E':'ender_eye','D':'diamond','C':'chorus_fruit','R':'redstone'})
recipe('dimension_suppressant',[' A ',' E ',' B '],{'A':'amethyst_shard','E':'ender_pearl','B':'glass_bottle'})

zh = {
 'item.dimensioncollapse.player_transmitter':'玩家传送器',
 'item.dimensioncollapse.player_transmitter.desc.1':'右键自动撤离当前维度的坍缩区域，无需接收箱。',
 'item.dimensioncollapse.player_transmitter.desc.2':'寻找远离所有坍缩前沿至少 512 格的安全落脚点。',
 'item.dimensioncollapse.player_transmitter.desc.3':'保留背包，可重复使用；成功后冷却 10 秒。',
 'message.dimensioncollapse.escape.unavailable':'当前维度没有坍缩，或当前状态无法撤离。',
 'message.dimensioncollapse.escape.searching':'正在定位远离坍缩的安全落脚点……',
 'message.dimensioncollapse.escape.failed':'未找到安全落脚点，请稍后重试。玩家与物品保持原位。',
 'message.dimensioncollapse.escape.success':'已撤离至三维区域：%s',
 'item.dimensioncollapse.dual_vector_foil':'二向箔', 'item.dimensioncollapse.foil_launcher':'二向箔发射器',
 'item.dimensioncollapse.resource_transmitter':'跨维资源传送器','block.dimensioncollapse.expansion_suppressor':'扩散抑制装置',
 'block.dimensioncollapse.transfer_receiver':'三维资源接收箱','entity.dimensioncollapse.dual_vector_foil':'二向箔',
 'container.dimensioncollapse.receiver':'三维资源接收箱',
 'item.dimensioncollapse.dual_vector_foil.desc.1':'右键投掷；也可作为发射器弹药。',
 'item.dimensioncollapse.dual_vector_foil.desc.2':'碰撞或飞行 5 秒后展开，地形变化不可逆。',
 'item.dimensioncollapse.foil_launcher.desc.1':'按住右键蓄力 1.5 秒，松开发射二向箔。',
 'item.dimensioncollapse.foil_launcher.desc.2':'优先使用副手弹药，其次使用背包中的二向箔。',
 'item.dimensioncollapse.foil_launcher.desc.3':'发射后冷却 5 秒；碰撞或飞行 5 秒后展开。',
 'item.dimensioncollapse.flat_matter.desc.1':'在二维状态下挖掘二维化空间获得。',
 'item.dimensioncollapse.flat_matter.desc.2':'为扩散抑制装置供能，每份可抑制 60 秒。',
 'block.dimensioncollapse.expansion_suppressor.desc.1':'放在坍缩前沿 64 格内，使用二维物质供能。',
 'block.dimensioncollapse.expansion_suppressor.desc.2':'每份燃料抑制 60 秒，仅工作时消耗；区块须保持加载。',
 'block.dimensioncollapse.expansion_suppressor.desc.3':'右键查看余量；空手潜行右键开关。',
 'block.dimensioncollapse.transfer_receiver.desc.1':'放在距离所有坍缩前沿至少 128 格的三维区域。',
 'block.dimensioncollapse.transfer_receiver.desc.2':'使用传送器右键绑定；右键打开 27 格存储空间。',
 'item.dimensioncollapse.resource_transmitter.desc.1':'先右键三维资源接收箱，绑定目的地。',
 'item.dimensioncollapse.resource_transmitter.desc.2':'进入二维状态后，主手持本装置，副手放待传送资源。',
 'item.dimensioncollapse.resource_transmitter.desc.3':'右键立即发送副手整组物品；目的地需远离坍缩。',
 'message.dimensioncollapse.launcher.no_foil':'没有二向箔弹药。',
 'message.dimensioncollapse.enabled':'启用','message.dimensioncollapse.disabled':'关闭',
 'message.dimensioncollapse.suppressor.status':'抑制器：%s｜剩余工作时间：%s 秒',
 'message.dimensioncollapse.transfer.bound':'已绑定接收箱：%s',
 'message.dimensioncollapse.transfer.link':'接收箱：%s / %s',
 'message.dimensioncollapse.transfer.outside':'请在二维状态下，主手持传送器使用。',
 'message.dimensioncollapse.transfer.unbound':'请先使用传送器右键绑定三维资源接收箱。',
 'message.dimensioncollapse.transfer.empty':'请把待传送资源放在副手。',
 'message.dimensioncollapse.transfer.missing':'接收箱不存在或目标维度不可用，物品已保留。',
 'message.dimensioncollapse.transfer.unsafe':'接收箱距离坍缩前沿不足 128 格，物品已保留。',
 'message.dimensioncollapse.transfer.full':'接收箱空间不足，物品已保留。',
 'message.dimensioncollapse.transfer.locked':'接收箱已锁定，物品已保留。',
 'message.dimensioncollapse.transfer.sent':'已传送 %s × %s 到三维接收箱。'
}
en = {
 'item.dimensioncollapse.player_transmitter':'Player Transmitter',
 'item.dimensioncollapse.player_transmitter.desc.1':'Use to escape collapses in this dimension. No receiver required.',
 'item.dimensioncollapse.player_transmitter.desc.2':'Finds safe footing at least 512 blocks beyond every collapse front.',
 'item.dimensioncollapse.player_transmitter.desc.3':'Keeps your inventory. Reusable, with a 10-second cooldown.',
 'message.dimensioncollapse.escape.unavailable':'No collapse in this dimension, or evacuation is unavailable in this state.',
 'message.dimensioncollapse.escape.searching':'Locating safe ground far outside the collapse…',
 'message.dimensioncollapse.escape.failed':'No safe landing found. Try again shortly. You and your items remain here.',
 'message.dimensioncollapse.escape.success':'Evacuated to three-dimensional space: %s',
 'item.dimensioncollapse.dual_vector_foil':'Dual-Vector Foil', 'item.dimensioncollapse.foil_launcher':'Foil Launcher',
 'item.dimensioncollapse.resource_transmitter':'Dimensional Resource Transmitter','block.dimensioncollapse.expansion_suppressor':'Expansion Suppressor',
 'block.dimensioncollapse.transfer_receiver':'3D Resource Receiver','entity.dimensioncollapse.dual_vector_foil':'Dual-Vector Foil',
 'container.dimensioncollapse.receiver':'3D Resource Receiver',
 'item.dimensioncollapse.dual_vector_foil.desc.1':'Right-click to throw, or load into a foil launcher.',
 'item.dimensioncollapse.dual_vector_foil.desc.2':'Unfolds on impact or after 5 seconds. Terrain changes are permanent.',
 'item.dimensioncollapse.foil_launcher.desc.1':'Hold use for 1.5 seconds, then release to fire.',
 'item.dimensioncollapse.foil_launcher.desc.2':'Uses foil from your offhand first, then your inventory.',
 'item.dimensioncollapse.foil_launcher.desc.3':'5-second cooldown; unfolds on impact or after 5 seconds.',
 'item.dimensioncollapse.flat_matter.desc.1':'Harvest flattened space while in two-dimensional form.',
 'item.dimensioncollapse.flat_matter.desc.2':'Powers an expansion suppressor for 60 seconds per piece.',
 'block.dimensioncollapse.expansion_suppressor.desc.1':'Place within 64 blocks of the front; fuel with flat matter.',
 'block.dimensioncollapse.expansion_suppressor.desc.2':'60 seconds per fuel, consumed only while working. Keep its chunk loaded.',
 'block.dimensioncollapse.expansion_suppressor.desc.3':'Use to inspect fuel; sneak-use with an empty hand to toggle.',
 'block.dimensioncollapse.transfer_receiver.desc.1':'Place at least 128 blocks outside every collapse front.',
 'block.dimensioncollapse.transfer_receiver.desc.2':'Use a transmitter to bind. Right-click to open 27 storage slots.',
 'item.dimensioncollapse.resource_transmitter.desc.1':'Use on a 3D resource receiver to bind a destination.',
 'item.dimensioncollapse.resource_transmitter.desc.2':'In 2D form, hold this in your main hand and cargo in your offhand.',
 'item.dimensioncollapse.resource_transmitter.desc.3':'Use to send the entire offhand stack instantly to a safe receiver.',
 'message.dimensioncollapse.launcher.no_foil':'No dual-vector foil ammunition.',
 'message.dimensioncollapse.enabled':'Enabled','message.dimensioncollapse.disabled':'Disabled',
 'message.dimensioncollapse.suppressor.status':'Suppressor: %s | Working time left: %s seconds',
 'message.dimensioncollapse.transfer.bound':'Receiver bound: %s',
 'message.dimensioncollapse.transfer.link':'Receiver: %s / %s',
 'message.dimensioncollapse.transfer.outside':'Use the transmitter in your main hand while in 2D form.',
 'message.dimensioncollapse.transfer.unbound':'Bind a 3D resource receiver first.',
 'message.dimensioncollapse.transfer.empty':'Put resources in your offhand.',
 'message.dimensioncollapse.transfer.missing':'Receiver or dimension unavailable. Items retained.',
 'message.dimensioncollapse.transfer.unsafe':'Receiver is less than 128 blocks outside a collapse. Items retained.',
 'message.dimensioncollapse.transfer.full':'Not enough receiver space. Items retained.',
 'message.dimensioncollapse.transfer.locked':'Receiver locked. Items retained.',
 'message.dimensioncollapse.transfer.sent':'Sent %s x %s to the 3D receiver.'
}
subtitles = ['空间坍缩','坍缩低鸣','空间碎裂','生物二维化','进入二维','离开二维','发射器蓄力','发射器就绪','二向箔发射','抑制器启动','抑制器停止','抑制器运转','二维物质充能','资源传送','资源接收','接收箱绑定','传送失败']
for key, label in zip(events, subtitles):
    zh[f'subtitles.dimensioncollapse.{key}'] = label
    en[f'subtitles.dimensioncollapse.{key}'] = key.replace('.', ' ').replace('_', ' ').title()
for locale, additions in [('zh_cn',zh),('en_us',en)]:
    path = ASSETS / f'lang/{locale}.json'
    data = json.loads(path.read_text(encoding='utf-8'))
    data.update(additions)
    write_json(path, data)
print('Generated pixel art, models, recipes, loot tables, 17 sound events, and translations.')
