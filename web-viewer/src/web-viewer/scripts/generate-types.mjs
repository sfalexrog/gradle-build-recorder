import { compileFromFile } from 'json-schema-to-typescript'
import { mkdir, writeFile } from 'node:fs/promises'
const schema = new URL('../../../../core/schemas/GradleBuildRecord.json', import.meta.url)
await mkdir(new URL('../src/generated', import.meta.url), { recursive: true })
await writeFile(
  new URL('../src/generated/recording.d.ts', import.meta.url),
  await compileFromFile(schema.pathname, {
    bannerComment:
      '/* Generated from core/schemas/GradleBuildRecord.json. Run npm run schema:types. */',
    style: { singleQuote: true, semi: false },
  }),
)
