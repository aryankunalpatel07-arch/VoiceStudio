import SwiftUI
import VoiceStudioKit

/// Ear-training game: works with no models, host or network (SPEC → Pitch game).
struct PitchGameView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.vsTheme) private var theme
    @AppStorage("pitch-game.best") private var best = 0
    @State private var game: PitchGame?
    @State private var feedback: String?
    @State private var newBest = false
    @State private var player = AudioPlayer()
    @State private var sound: Task<Void,Never>?
    var body: some View {
        Page {
            if let game {
                Card {
                    Text(model.t("mobile.game.score",["score": String(game.score)])).font(.headline).accessibilityIdentifier("game.score")
                    Text(model.t("mobile.game.best",["score": String(best)])).foregroundStyle(theme.color("textMuted"))
                    Text(model.t("mobile.game.lives",["count": String(game.lives)])).accessibilityIdentifier("game.lives")
                    Text(model.t("mobile.game.gap",["cents": String(Int(game.cents.rounded()))])).foregroundStyle(theme.color("textMuted"))
                }
                if let feedback { Text(model.t(feedback)) }
                if game.over {
                    Text(model.t("mobile.game.over")).font(.headline).accessibilityAddTraits(.isHeader)
                    if newBest { Text(model.t("mobile.game.new_best")) }
                    Button(model.t("mobile.game.play_again"),systemImage: "play.fill") { start() }.studioButton(.primary)
                } else {
                    Button(model.t("mobile.game.higher"),systemImage: "chevron.up") { answer(true) }.studioButton(.primary)
                    Button(model.t("mobile.game.lower"),systemImage: "chevron.down") { answer(false) }.studioButton(.primary)
                    Button(model.t("mobile.game.replay"),systemImage: "arrow.clockwise") { play() }.studioButton(.secondary)
                }
            } else {
                Text(model.t("mobile.game.intro"))
                if best > 0 { Text(model.t("mobile.game.best",["score": String(best)])).foregroundStyle(theme.color("textMuted")) }
                Button(model.t("mobile.game.start"),systemImage: "play.fill") { start() }.studioButton(.primary)
            }
        }
        .studioNavigationTitle(model.t("mobile.game.title"))
        .onDisappear { silence() }
    }
    private func start() { game = PitchGame(); feedback = nil; newBest = false; play() }
    private func answer(_ higher: Bool) {
        guard var current = game, !current.over else { return }
        let key = current.answer(higher: higher) ? "mobile.game.correct" : "mobile.game.wrong"
        game = current; feedback = key
        if current.score > best { best = current.score; newBest = true }
        UIAccessibility.post(notification: .announcement,argument: model.t(current.over ? "mobile.game.over" : key))
        if current.over { silence() } else { play() }
    }
    private func silence() { sound?.cancel(); sound = nil; player.clear() }
    private func play() {
        guard let round = game?.round else { return }
        sound?.cancel()
        sound = Task {
            do {
                try await player.load(PitchGame.wave(round),extension: "wav")
                if !Task.isCancelled { player.play() }
            } catch is CancellationError {
            } catch { model.error = ClientError("mobile.errors.audio") }
        }
    }
}
