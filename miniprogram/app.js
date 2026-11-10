const api = require('./utils/api')
App({
  onLaunch() { api.session() },
  globalData: { selectedAddress: null },
})
