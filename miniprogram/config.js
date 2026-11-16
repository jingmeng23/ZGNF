// Public values only. Add these hosts to the mini-program request/uploadFile/downloadFile domain lists.
// Never put appSecret, merchant private keys or the API v3 key in this file.
module.exports = {
  apiBase: 'http://192.168.2.95:8081/api/v1',
  // Optional server for additional static images. Existing Pepsi/Weiyi covers are bundled locally.
  assetBase: 'http://192.168.2.95:8082',
}
