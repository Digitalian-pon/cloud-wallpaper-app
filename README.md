# Cloud Wallpaper

Android向けの壁紙スライドショー・フォトフレーム・インターネットTVアプリ。

## 機能

### 壁紙スライドショー
- ボタン一つでホーム画面とロック画面の壁紙を自動切替
- Google Drive / OneDrive / Dropbox / 端末内の画像に対応
- 切替間隔: 1分 / 5分 / 15分 / 30分 / 1時間 / 3時間 / 6時間
- 表示順: ランダム / 順番通り
- スケーリング: 画面いっぱい（切り抜き） / 画面に収める（余白あり）

### フォトフレーム
- フルスクリーン＋画面常時ONのスライドショー
- クロスフェードトランジション
- タップで時計表示 / ダブルタップで終了 / スワイプで前後の画像

### インターネットTV
- YouTube / AbemaTV / TVer等をフルスクリーンで視聴

## 技術仕様

| 項目 | 値 |
|---|---|
| 言語 | Kotlin |
| minSdk | 26 (Android 8.0) |
| targetSdk | 34 (Android 14) |
| ビルドツール | Gradle 8.5 |
| CI/CD | GitHub Actions |

### アーキテクチャ
- 短い間隔（1分・5分）: **Foreground Service** で正確なタイマー実行
- 長い間隔（15分以上）: **WorkManager** でバッテリー効率の良い定期実行
- 画像読み込み: Storage Access Framework (SAF) でクラウドストレージ対応

## ビルド

GitHub Actionsで自動ビルド:
1. `main`ブランチにpush
2. Actionsが自動実行
3. Artifactから署名済みAPKをダウンロード

## ライセンス

MIT License
