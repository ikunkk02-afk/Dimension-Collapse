#version 150

uniform sampler2D SceneSampler;
uniform sampler2D DepthSampler;

// All positions are camera-relative world space.
uniform mat4 ViewProj;
uniform mat4 InvViewProj;
uniform vec3 Center;
uniform float PlaneY;
uniform float Radius;
uniform float Speed;
uniform float Time;
// 0 = normal view, 1 = a two-dimensional being's view: only the line through the crosshair, stretched to fill the screen.
uniform float Flatland;

in vec2 texCoord;

out vec4 fragColor;

const float SKY_DIST = 1.0e7;
const float SHEET_THICKNESS = 0.0625;
// Flattened creatures lie on top of the sheet, squashed to a few hundredths of a block.
const float FLAT_ENTITY_HEIGHT = 0.1;
// Fraction of the screen height a two-dimensional viewer actually perceives.
const float FLATLAND_BAND = 0.08;
const int WAVE_COUNT = 3;
const vec3 FRONT_COLOR = vec3(0.55, 0.75, 1.0);

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 hash33(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.xxy + p.yxx) * p.zyx);
}

float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1.0, 0.0)), u.x),
               mix(hash12(i + vec2(0.0, 1.0)), hash12(i + vec2(1.0, 1.0)), u.x), u.y);
}

float fbm(vec2 p) {
    float value = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 4; i++) {
        value += amp * valueNoise(p);
        p = p * 2.03 + vec2(17.1, 9.2);
        amp *= 0.5;
    }
    return value;
}

// x: distance to the nearest cell border, y: random value per cell.
vec2 cells(vec3 p) {
    vec3 base = floor(p);
    vec3 f = fract(p);
    float d1 = 8.0;
    float d2 = 8.0;
    vec3 nearest = base;
    for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
            for (int z = -1; z <= 1; z++) {
                vec3 g = vec3(float(x), float(y), float(z));
                vec3 r = g + hash33(base + g) - f;
                float d = dot(r, r);
                if (d < d1) {
                    d2 = d1;
                    d1 = d;
                    nearest = base + g;
                } else if (d < d2) {
                    d2 = d;
                }
            }
        }
    }
    return vec2(sqrt(d2) - sqrt(d1), hash12(nearest.xy + nearest.z * 17.0));
}

vec3 hsv2rgb(vec3 c) {
    vec3 p = abs(fract(c.xxx + vec3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0);
    return c.z * mix(vec3(1.0), clamp(p - 1.0, 0.0, 1.0), c.y);
}

vec3 unproject(vec2 uv, float depth) {
    vec4 p = InvViewProj * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}

// Returns false when the point is behind the camera.
bool toScreen(vec3 p, out vec2 uv) {
    vec4 clip = ViewProj * vec4(p, 1.0);
    uv = clip.xy / max(clip.w, 1.0e-4) * 0.5 + 0.5;
    return clip.w > 0.0;
}

bool onScreen(vec2 uv) {
    return uv.x >= 0.0 && uv.x <= 1.0 && uv.y >= 0.0 && uv.y <= 1.0;
}

float sceneDistance(vec2 uv) {
    float d = texture(DepthSampler, uv).r;
    return d >= 1.0 ? SKY_DIST : length(unproject(uv, d));
}

vec3 sampleScene(vec2 uv, float aberration) {
    vec2 off = (uv - 0.5) * aberration;
    return vec3(texture(SceneSampler, uv + off).r,
                texture(SceneSampler, uv).g,
                texture(SceneSampler, uv - off).b);
}

// Entry distance of the ray (from the camera) into a sphere around Center, or -1 on a miss.
float sphereEntry(vec3 dir, float radius) {
    float b = dot(dir, Center);
    float disc = b * b - (dot(Center, Center) - radius * radius);
    if (disc <= 0.0) return -1.0;
    float t = b - sqrt(disc);
    return t > 0.0 ? t : -1.0;
}

// Radius and strength of the k-th shockwave running ahead of the front.
vec2 wave(int k) {
    float span = 10.0 + Radius * 0.4;
    float phase = fract(Time * 0.45 + float(k) / float(WAVE_COUNT));
    return vec2(Radius + phase * span, (1.0 - phase) * (1.0 - phase));
}

// What is left of 3D space after it has been folded away.
vec3 voidColor(vec3 dir) {
    vec2 sp = vec2(atan(dir.z, dir.x), asin(clamp(dir.y, -1.0, 1.0)));
    float nebula = fbm(sp * 3.0 + vec2(Time * 0.02, 0.0));
    vec3 col = mix(vec3(0.004, 0.005, 0.012), vec3(0.03, 0.018, 0.07), nebula);
    vec2 cell = floor(sp * 180.0);
    float star = step(0.9965, hash12(cell));
    float twinkle = 0.6 + 0.4 * sin(Time * 3.0 + hash12(cell + 7.0) * 40.0);
    return col + vec3(star * twinkle * 0.8);
}

// Faint procedural picture where the plane holds no flattened matter.
vec3 ghostPainting(vec3 hit) {
    vec2 q = hit.xz - Center.xz;
    float d = length(q);
    float nd = clamp(d / max(Radius, 1.0e-3), 0.0, 1.0);
    vec2 radial = d > 1.0e-4 ? q / d : vec2(1.0, 0.0);
    float angle = atan(q.y, q.x);

    float strata = nd * 14.0 + fbm(vec2(angle * 3.0, nd * 6.0)) * 2.5 - Time * 0.15;
    float band = floor(strata);
    float bandFrac = fract(strata);

    vec3 col = hsv2rgb(vec3(fract(hash12(vec2(band, 3.1)) * 0.35 + 0.55 + nd * 0.2), 0.55, 0.85));
    vec3 source = vec3(radial.x * (Radius + 1.0), (hash12(vec2(band, 9.7)) - 0.5) * Radius * 0.6, radial.y * (Radius + 1.0)) + Center;
    vec2 suv;
    if (toScreen(source, suv) && onScreen(suv) && sceneDistance(suv) < SKY_DIST) {
        col = mix(col, texture(SceneSampler, suv).rgb * 1.15, 0.7);
    }

    col = floor(col * 6.0 + 0.5) / 6.0;
    float ink = smoothstep(0.0, 0.06, bandFrac) * smoothstep(1.0, 0.94, bandFrac);
    col *= mix(0.25, 1.0, ink);
    col += FRONT_COLOR * pow(nd, 18.0) * 1.5;
    return col;
}

// The real flattened blocks, restyled so they read as a picture rather than as geometry.
vec3 sheet(vec3 hit, vec3 base) {
    vec2 q = hit.xz - Center.xz;
    float nd = clamp(length(q) / max(Radius, 1.0e-3), 0.0, 1.0);
    vec3 col = floor(base * 7.0 + 0.5) / 7.0;

    vec2 cell = fract(q + 0.5);
    float edge = min(min(cell.x, 1.0 - cell.x), min(cell.y, 1.0 - cell.y));
    col *= mix(0.72, 1.0, smoothstep(0.0, 0.06, edge));

    float sheen = pow(fbm(q * 0.08 + vec2(Time * 0.12, -Time * 0.07)), 3.0);
    col += hsv2rgb(vec3(fract(0.55 + sheen + Time * 0.02), 0.5, 1.0)) * sheen * 0.35;
    col += FRONT_COLOR * pow(nd, 24.0) * 1.2;
    return col;
}

vec3 shellFilm(vec3 n, vec3 dir, float energy) {
    float facing = abs(dot(n, dir));
    float fresnel = pow(1.0 - facing, 4.0);
    vec2 sp = vec2(atan(n.z, n.x), asin(clamp(n.y, -1.0, 1.0)));
    float flow = fbm(sp * vec2(6.0, 4.0) * (1.0 + Radius * 0.02) + vec2(Time * 0.3, -Time * 0.2));
    float lines = smoothstep(0.45, 0.5, abs(fract(flow * 8.0) - 0.5));
    vec3 irid = hsv2rgb(vec3(fract(flow * 1.5 + facing * 0.6 + Time * 0.03), 0.6, 1.0));

    // Space breaking into facets as it is folded flat.
    vec2 facet = cells(n * (Radius / 3.0) + vec3(0.0, Time * 0.05, 0.0));
    float border = 1.0 - smoothstep(0.0, 0.08, facet.x);
    float pulse = pow(max(sin(facet.y * 40.0 + Time * 1.2), 0.0), 12.0);
    vec3 tile = hsv2rgb(vec3(fract(facet.y * 0.3 + 0.55), 0.5, 1.0));

    return irid * (fresnel * 1.4 + lines * 0.12)
         + tile * (border * 0.25 + pulse * 0.3) * energy * (0.4 + fresnel);
}

// The plane spilling outward past the front, like an accretion disk.
vec3 spillDisk(vec3 hit, vec3 dir, float energy) {
    vec2 q = hit.xz - Center.xz;
    float h = length(q);
    float span = 6.0 + Radius * 0.5;
    float x = (h - Radius) / span;
    if (x < 0.0 || x > 1.0) return vec3(0.0);
    float angle = atan(q.y, q.x);
    float swirl = fbm(vec2(angle * 6.0 + x * 4.0 - Time * 0.6, x * 8.0 - Time * 1.5));
    float streaks = 0.35 + 0.65 * pow(swirl, 2.0);
    float rim = exp(-(h - Radius) * 1.5);
    float grazing = mix(0.3, 1.0, smoothstep(0.0, 0.15, abs(dir.y)));
    vec3 tint = hsv2rgb(vec3(0.58 + 0.1 * swirl, 0.5, 1.0));
    return (tint * (1.0 - x) * (1.0 - x) * streaks * 0.6 + FRONT_COLOR * rim) * energy * grazing;
}

void main() {
    float energy = 1.0 + clamp(Speed / 6.0, 0.0, 2.0);
    float camShell = length(Center) - Radius;
    bool camInside = camShell < 0.0;
    // Strongest right at the front; deep inside the collapsed region it settles to a mild level.
    float prox = camInside
            ? mix(1.0, 0.25, smoothstep(0.0, 12.0, -camShell))
            : 1.0 - smoothstep(0.0, 24.0, camShell);

    // The view itself gets flattened as the front closes in.
    float squash = (1.0 + prox * prox * 1.5) * mix(1.0, FLATLAND_BAND, Flatland);
    vec2 uv = vec2(texCoord.x, 0.5 + (texCoord.y - 0.5) * squash);
    if (!onScreen(uv)) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    vec3 dir = normalize(unproject(uv, 1.0));
    float sceneDist = sceneDistance(uv);
    float b = dot(dir, Center);
    float c = dot(Center, Center) - Radius * Radius;
    float disc = b * b - c;

    vec2 centerUv;
    bool centerVisible = toScreen(Center, centerUv);
    vec2 sampleUv = uv;
    float lens = 0.0;
    vec3 glow = vec3(0.0);

    if (!camInside && b > 0.0 && centerVisible) {
        // Space just outside the sphere is dragged toward it.
        float miss = sqrt(max(dot(Center, Center) - b * b, 0.0));
        float halo = Radius * 0.6 + 3.0;
        if (miss > Radius) {
            lens = 1.0 - clamp((miss - Radius) / halo, 0.0, 1.0);
            float behind = smoothstep(b - Radius - 2.0, b - Radius + 2.0, sceneDist);
            float pull = lens * lens * 0.25 * behind;
            float twist = lens * lens * 0.6 * behind;
            mat2 rot = mat2(cos(twist), -sin(twist), sin(twist), cos(twist));
            sampleUv = uv - rot * (centerUv - uv) * pull;
        }

        // Shockwave shells: a refractive ripple in the air plus a faint rim.
        vec2 away = normalize(uv - centerUv + 1.0e-5);
        for (int k = 0; k < WAVE_COUNT; k++) {
            vec2 w = wave(k);
            float tw = sphereEntry(dir, w.x);
            if (tw > 0.0 && tw < sceneDist) {
                vec3 n = normalize(dir * tw - Center);
                float rim = pow(1.0 - abs(dot(n, dir)), 6.0) * w.y;
                sampleUv += away * rim * 0.012 * energy;
                glow += FRONT_COLOR * rim * 0.35 * energy;
            }
        }
        sampleUv = clamp(sampleUv, vec2(0.0), vec2(1.0));
    }

    vec3 col = sampleScene(sampleUv, prox * 0.02 + lens * lens * 0.01);

    bool swallowed = false;
    if (disc > 0.0) {
        float sq = sqrt(disc);
        float t0 = b - sq;
        float t1 = b + sq;
        float tEnter = max(t0, 0.0);
        if (t1 > 0.0 && tEnter < sceneDist) {
            swallowed = true;
            vec3 p = dir * sceneDist;
            float eps = 0.03 + sceneDist * 0.0015;
            bool onSheet = sceneDist < t1 && p.y > PlaneY - SHEET_THICKNESS - eps && p.y < PlaneY + FLAT_ENTITY_HEIGHT + eps;
            if (onSheet) {
                col = sheet(p, col);
            } else {
                vec3 inner = voidColor(dir);
                if (sceneDist > t1) {
                    inner = mix(inner, col * vec3(0.55, 0.6, 0.8), 0.45);
                }
                if (abs(dir.y) > 1.0e-5) {
                    float tp = PlaneY / dir.y;
                    if (tp > tEnter && tp < t1) {
                        inner = mix(inner, ghostPainting(dir * tp), 0.35);
                    }
                }
                col = inner;
            }
            if (!camInside) {
                col += shellFilm(normalize(dir * tEnter - Center), dir, energy);
            }
        }
    }

    if (!swallowed) {
        if (sceneDist < SKY_DIST) {
            float s = length(dir * sceneDist - Center) - Radius;
            // Glowing seam where the front is eating into terrain.
            if (s >= 0.0 && s < 1.5) {
                float k = 1.0 - s / 1.5;
                float flicker = 0.7 + 0.3 * valueNoise(vec2(atan(dir.z, dir.x) * 40.0, Time * 4.0));
                col += FRONT_COLOR * k * k * flicker * 1.6 * energy;
            }
            // Shockwaves sweeping across the ground.
            for (int k = 0; k < WAVE_COUNT; k++) {
                vec2 w = wave(k);
                float band = 1.0 - smoothstep(0.0, 0.6, abs(s + Radius - w.x));
                col += FRONT_COLOR * band * w.y * 0.9 * energy;
            }
        }
        if (abs(dir.y) > 1.0e-5) {
            float tp = PlaneY / dir.y;
            if (tp > 0.0 && tp < sceneDist) {
                col += spillDisk(dir * tp, dir, energy);
            }
        }
        col += glow;
    }

    // Flatlanders judge distance by fog alone.
    col = mix(col, vec3(0.015, 0.02, 0.045), Flatland * smoothstep(2.0, 40.0, min(sceneDist, 1000.0)));

    float vignette = smoothstep(1.2, 0.3, length(texCoord - 0.5) * 1.4);
    col *= mix(1.0, vignette, prox * 0.8);
    col = mix(col, col * vec3(0.8, 0.9, 1.2), prox * 0.5);

    fragColor = vec4(col, 1.0);
}
