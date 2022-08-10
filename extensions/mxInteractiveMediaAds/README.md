## Ad stitching module for ExoPlayer AdsMediaSource & Mx-Video-In-Stream-ads sdk

### Module responsibilities
- Lifecycle observer of Exo Player
- Interact with mx video ad sdk
- Adapter for player and ad sdk



![Player Lifecycle observer](docs/Exo%20-_%20AdLoaderImpl.png)


 > MxMediaAdLoader keeps mapping of adTagLoaders and adId's. It delegate player lifecycle calls to AdTagLoder. Multiple AdTagLoader are created in case playlist pass to exo player for playing ads



![How AdTagLoader works](docs/AdTagLoader%20_Mx-Ads-Lib2.png)

