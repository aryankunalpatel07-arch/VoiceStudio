import Foundation
import Testing
@testable import VoiceStudioKit

private func scripted(_ draws: [Double]) -> PitchGame {
    var values = draws.makeIterator()
    return PitchGame { values.next()! }
}

// The same vectors run in Android PitchGameTest.kt: both apps must draw the same rounds.
@Test func pitchGameRoundsNarrowAfterRightAnswersAndKeepTheGapAfterMisses() {
    var game = scripted([0.0, 0.9, 0.5, 0.1, 0.0, 0.9, 0.0, 0.9, 0.0, 0.9])
    #expect(abs(game.round.first - 220) < 1e-9)
    #expect(abs(game.round.second - 277.18263097687205) < 1e-6)
    #expect(game.round.higher)
    // Answers are taken outside #expect: the macro cannot call a mutating method.
    let first = game.answer(higher: true); #expect(first)
    #expect(game.score == 1); #expect(game.lives == 3); #expect(abs(game.cents - 340) < 1e-9)
    #expect(abs(game.round.first - 311.1269837220809) < 1e-6)
    #expect(!game.round.higher)
    #expect(abs(1200 * log2(game.round.second / game.round.first) + 340) < 1e-6)
    let second = game.answer(higher: true); #expect(!second)
    #expect(game.score == 1); #expect(game.lives == 2); #expect(abs(game.cents - 340) < 1e-9)
    let third = game.answer(higher: false); #expect(!third); #expect(!game.over)
    let fourth = game.answer(higher: false); #expect(!fourth); #expect(game.over)
    #expect(game.score == 1)
}

@Test func pitchGameGapNeverDropsBelowTenCents() {
    #expect(abs(PitchGame.cents(0) - 400) < 1e-9)
    #expect(abs(PitchGame.cents(200) - 10) < 1e-9)
    #expect((0..<200).allSatisfy { PitchGame.cents($0 + 1) <= PitchGame.cents($0) })
}

@Test func pitchGameSamplesHoldTwoFadedTonesAroundSilence() {
    let samples = PitchGame.samples(PitchRound(first: 220, second: 440), sampleRate: 8000)
    #expect(samples.count == 3600 * 2 + 1600)
    #expect(samples[0] == 0); #expect(samples[3599] == 0)
    #expect(samples[3600..<5200].allSatisfy { $0 == 0 })
    func crossings(_ range: Range<Int>) -> Int { zip(range, range.dropFirst()).filter { samples[$0] < 0 && samples[$1] >= 0 }.count }
    #expect(abs(crossings(0..<3600) - 99) <= 1)
    #expect(abs(crossings(5200..<8800) - 198) <= 1)
    #expect((16000...16384).contains(samples.map { abs(Int($0)) }.max()!))
}

@Test func pitchGameWaveIsACanonicalPCM16File() {
    let wave = PitchGame.wave(PitchRound(first: 220, second: 440), sampleRate: 8000)
    #expect(wave.count == 44 + 8800 * 2)
    #expect(String(decoding: wave[0..<4], as: UTF8.self) == "RIFF")
    #expect(String(decoding: wave[8..<16], as: UTF8.self) == "WAVEfmt ")
    #expect(wave[24..<28].elementsEqual([0x40, 0x1F, 0, 0]))
    #expect(String(decoding: wave[36..<40], as: UTF8.self) == "data")
    #expect(wave[40..<44].elementsEqual([0xC0, 0x44, 0, 0]))
}
