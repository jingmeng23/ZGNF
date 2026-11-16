const fs = require('node:fs')
const http = require('node:http')
const path = require('node:path')

const types = { '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.gif': 'image/gif', '.webp': 'image/webp', '.avif': 'image/avif', '.bmp': 'image/bmp', '.ico': 'image/x-icon' }
function inside(root, file) {
  const relative = path.relative(root, file)
  return relative !== '..' && !relative.startsWith('..' + path.sep) && !path.isAbsolute(relative)
}
function createAssetServer(directory) {
  const root = fs.realpathSync(directory)
  if (!fs.statSync(root).isDirectory()) throw new Error('图片目录不存在：' + directory)
  return http.createServer(async (request, response) => {
    response.setHeader('Access-Control-Allow-Origin', '*')
    response.setHeader('X-Content-Type-Options', 'nosniff')
    response.setHeader('Cache-Control', 'no-cache')
    const reply = (status, message) => {
      response.writeHead(status, { 'Content-Type': 'text/plain; charset=utf-8' })
      response.end(request.method === 'HEAD' ? undefined : message)
    }
    if (!['GET', 'HEAD'].includes(request.method)) {
      response.setHeader('Allow', 'GET, HEAD')
      return reply(405, 'Method not allowed')
    }
    let pathname
    try { pathname = decodeURIComponent((request.url || '').split('?')[0]) }
    catch { return reply(400, 'Invalid image path') }
    if (!pathname.startsWith('/images/')) return reply(404, 'Image not found')
    const relative = pathname.slice('/images/'.length)
    if (/[\\\x00-\x1f]/.test(relative) || relative.split('/').some(part => part.startsWith('.'))) return reply(400, 'Invalid image path')
    const type = types[path.extname(relative).toLowerCase()]
    if (!type) return reply(404, 'Image not found')
    let handle
    try {
      const target = path.resolve(root, relative)
      if (!inside(root, target)) return reply(404, 'Image not found')
      const resolved = await fs.promises.realpath(target)
      if (!inside(root, resolved)) return reply(404, 'Image not found')
      handle = await fs.promises.open(resolved, 'r')
      const stat = await handle.stat()
      if (!stat.isFile()) return reply(404, 'Image not found')
      response.writeHead(200, { 'Content-Type': type, 'Content-Length': stat.size, 'Last-Modified': stat.mtime.toUTCString() })
      if (request.method === 'HEAD') return response.end()
      const stream = handle.createReadStream()
      handle = null // The stream now owns and closes the file handle.
      stream.on('error', () => response.destroy())
      response.on('close', () => stream.destroy())
      stream.pipe(response)
    } catch (error) {
      if (!response.headersSent) reply(['ENOENT', 'ENOTDIR', 'EACCES', 'EPERM'].includes(error.code) ? 404 : 500, 'Image unavailable')
      else response.destroy()
    } finally {
      if (handle) await handle.close()
    }
  })
}

if (require.main === module) {
  try {
    const root = path.resolve(process.env.MINI_ASSET_ROOT || path.join(__dirname, '../../frontend/public/images'))
    const host = process.env.MINI_ASSET_HOST || '0.0.0.0'
    const port = Number(process.env.MINI_ASSET_PORT || 8082)
    if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('MINI_ASSET_PORT 必须是 1 到 65535 之间的端口')
    const server = createAssetServer(root)
    server.on('error', error => {
      console.error(error.code === 'EADDRINUSE' ? `端口 ${port} 已被占用，请检查现有图片服务或设置 MINI_ASSET_PORT 并同步修改 config.js。` : error.message)
      process.exitCode = 1
    })
    server.listen(port, host, () => {
      console.log(`本地图片服务已启动，监听 ${host}:${port}，路径 /images/`)
      console.log('图片目录：' + root)
      console.log('请保持此终端运行，并在 config.js 的 assetBase 中使用本机局域网地址。')
    })
    for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => server.close())
  } catch (error) {
    console.error('图片服务启动失败：' + error.message)
    process.exitCode = 1
  }
}

module.exports = { createAssetServer }
