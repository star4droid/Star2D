# Ads (Google AdMob)

Code: `ads/AdsManager.java`. Test ids are used unless real ones are supplied (env / `-P`): `ADMOB_APP_ID`, `ADMOB_BANNER_ID`, `ADMOB_INTERSTITIAL_ID`, `ADMOB_REWARDED_ID` (GitHub secrets, wired in `build-aab.yml`).

Behaviour: adaptive banner on the projects screen only; one interstitial when a project is closed (not in the first 3 min, from the 2nd close, max 1 per 5 min); optional rewarded video on the Support page = 1 h without ads; no ads while logged in && paid && balance >= $1. UMP consent form is shown where required (EEA/UK).

Setup: AdMob account -> add app (Android, com.star4droid.star2d.evo) -> create Banner, Interstitial, Rewarded units -> add the 4 ids as GitHub secrets -> publish `app-ads.txt` on the developer website (shown in AdMob) -> Play Console: App content -> Ads = yes, Data safety (Advertising ID, device ids, usage data shared with Google). Never tap your own live ads; test devices must use test ids.
