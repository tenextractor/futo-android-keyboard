package org.futo.inputmethod.event.combiners

import android.text.TextUtils
import org.futo.inputmethod.event.Combiner
import org.futo.inputmethod.event.Event
import org.futo.inputmethod.latin.common.Constants

class KoreanCombiner: Combiner {

    private val initials = listOf(
        'ㄱ' /*g*/,  'ㄲ' /*gg*/, 'ㄴ' /*n*/,  'ㄷ' /*d*/,
        'ㄸ' /*dd*/, 'ㄹ' /*r*/,  'ㅁ' /*m*/,  'ㅂ' /*b*/,
        'ㅃ' /*bb*/, 'ㅅ' /*s*/,  'ㅆ' /*ss*/, 'ㅇ' /*ng*/,
        'ㅈ' /*j*/,  'ㅉ' /*jj*/, 'ㅊ' /*ch*/, 'ㅋ' /*k*/,
        'ㅌ' /*t*/,  'ㅍ' /*p*/,  'ㅎ' /*h*/
    ) // Only these characters can appear at the START of a Hangul syllable,
    // and any syllable MUST have one of these

    private val doubleableInitials = listOf('ㄱ', 'ㄷ', 'ㅂ', 'ㅅ', 'ㅈ')
    // These initials can be doubled, by adding 1 to the codepoint number.
    // For example, ㄱ = U+3131, ㄲ = U+3132

    private val vowels = listOf(
        'ㅏ' /*a*/,   'ㅐ' /*ae*/,  'ㅑ' /*ya*/,   'ㅒ' /*yae*/,
        'ㅓ' /*eo*/,  'ㅔ' /*e*/,   'ㅕ' /*yeo*/,  'ㅖ' /*ye*/,
        'ㅗ' /*o*/,   'ㅘ' /*o+a*/, 'ㅙ' /*o+ae*/, 'ㅚ' /*o+i*/,
        'ㅛ' /*yo*/,  'ㅜ' /*u*/,   'ㅝ' /*u+eo*/, 'ㅞ' /*u+e*/,
        'ㅟ' /*u+i*/, 'ㅠ' /*yu*/,  'ㅡ' /*eu*/,   'ㅢ' /*eu+i*/,
        'ㅣ' /*i*/
    ) // Only these characters can appear in the MIDDLE (as a vowel) of a Hangul syllable,
    // and any syllable MUST have one of these

    private val finals = listOf(
        null,       'ㄱ' /*g*/,  'ㄲ' /*gg*/, 'ㄳ' /*gs*/,
        'ㄴ' /*n*/,  'ㄵ' /*nj*/, 'ㄶ' /*nh*/, 'ㄷ' /*d*/,
        'ㄹ' /*r*/,  'ㄺ' /*rg*/, 'ㄻ' /*rm*/, 'ㄼ' /*rb*/,
        'ㄽ' /*rs*/, 'ㄾ' /*rt*/, 'ㄿ' /*rp*/, 'ㅀ' /*rh*/,
        'ㅁ' /*m*/,  'ㅂ' /*b*/,  'ㅄ' /*bs*/, 'ㅅ' /*s*/,
        'ㅆ' /*ss*/, 'ㅇ' /*ng*/, 'ㅈ' /*j*/,  'ㅊ' /*ch*/,
        'ㅋ' /*k*/,  'ㅌ' /*t*/,  'ㅍ' /*p*/,  'ㅎ' /*h*/
    ) // Only these characters can appear at the END of a Hangul syllable.
    // These are optional, and any syllable can only have either no final, or one of these finals.
    // The NULL at index 0 represents the possibility of having no final.

    private val GA_LOCATION = '가'.code //44032
    // This value is important because, it is the start of the Unicode Hangul Syllables block,
    // and the Unicode codepoint (U+XXXX number) for any Hangul syllable is given by the formula:
    // syllable = [(initial) × 588 + (vowel) × 28 + (final)] + 44032
    // where `initial`, `vowel` and `final` are the indices of the initial, vowel,
    // and final (0 if no final) character in the above lists.

    private val mergedClusters = mapOf(
        "ㅗㅏ" to 'ㅘ',
        "ㅗㅐ" to 'ㅙ',
        "ㅗㅣ" to 'ㅚ',
        "ㅜㅓ" to 'ㅝ',
        "ㅜㅔ" to 'ㅞ',
        "ㅜㅣ" to 'ㅟ',
        "ㅡㅣ" to 'ㅢ', //vowels

        "ㅏㅏ" to 'ㅑ',
        "ㅐㅐ" to 'ㅒ',
        "ㅓㅓ" to 'ㅕ',
        "ㅔㅔ" to 'ㅖ',
        "ㅗㅗ" to 'ㅛ',
        "ㅜㅜ" to 'ㅠ', //gboard 단모음 layout uses these mappings

        "ㄱㄱ" to 'ㄲ',
        "ㄱㅅ" to 'ㄳ',
        "ㄴㅈ" to 'ㄵ',
        "ㄴㅎ" to 'ㄶ',
        "ㄹㄱ" to 'ㄺ',
        "ㄹㅁ" to 'ㄻ',
        "ㄹㅂ" to 'ㄼ',
        "ㄹㅅ" to 'ㄽ',
        "ㄹㅌ" to 'ㄾ',
        "ㄹㅍ" to 'ㄿ',
        "ㄹㅎ" to 'ㅀ',
        "ㅂㅅ" to 'ㅄ',
        "ㅅㅅ" to 'ㅆ' //finals
    ) // The `initials`, `vowels`, and `finals` mentioned above are not the simplest possible
    // elements, and mostly need to be constructed from multiple keypresses.
    // This along with the following map make this possible.

    private fun toBlock(initial: Char, vowel: Char, final: Char?): Char {
        //merge initial, vowel and optional final letters into a hangul syllable block
        //using this formula: syllable = [(initial) × 588 + (vowel) × 28 + (final)] + 44032
        return (GA_LOCATION + 588*initials.binarySearch(initial) + 28*vowels.binarySearch(vowel) +
                if (final != null) finals.binarySearch(final) else 0).toChar()
    }
    private fun isLetter(char: Char): Boolean { return char.code in 0x3131..0x3163}
    private fun isInitial(char: Char): Boolean { return initials.binarySearch(char) >= 0 }
    private fun isVowel(char: Char): Boolean { return char.code in 0x314F..0x3163 }
    private fun isFinal(char: Char): Boolean { return finals.binarySearch(char) >= 0 }


    val buffer = StringBuilder() // This buffer holds a single word of UNCOMBINED Hangul letters.

    private fun toBlocks(): CharSequence {
        val combined = StringBuilder()

        var initial: Char? = null
        var vowel: Char? = null
        var final: Char? = null
        var final2: Char? = null // State variables

        for (char in buffer) {
            if (initial == null) { // Starting case
                if (initials.binarySearch(char) < 0) {
                    combined.append(char)
                    continue
                } // If the current char is not an initial, just add it to the result
                initial = char
                continue
            }

            if (vowel == null) { // There is an initial, but no vowel
                if (initial == char && doubleableInitials.binarySearch(initial) >= 0) {
                    initial = (initial.code + 1).toChar()
                    continue
                } // If current char is the same as the initial, then double the initial
                // (adding 1 to ㄱ makes it ㄲ and so on)

                if (!isVowel(char)) {
                    combined.append(initial)

                    if (!isInitial(char)) {
                        combined.append(char)
                        initial = null
                        continue
                    }
                    initial = char
                    continue
                } // After the double initial case earlier has been handled,
                // if current char is not a vowel at this point, we are not getting a valid syllable
                // Just output individual letters as they are

                vowel = char // char is a valid vowel
                continue
            }

            if (final == null) { // There is initial and vowel, but no final
                val possibleCluster = vowel.toString() + char
                if (mergedClusters.containsKey(possibleCluster)) {
                    vowel = mergedClusters[possibleCluster]!!
                    continue
                } // If char is another vowel character that can merge with the existing vowel,
                // merge it

                if (!isFinal(char)) {
                    combined.append(toBlock(initial, vowel, null))
                    vowel = null
                    if (!isInitial(char)) {
                        combined.append(char)
                        initial = null
                        continue
                    }
                    initial = char
                    continue
                } // current char is not a valid final

                final = char // char is a valid final
                continue
            }

            if (final2 == null) {
                val possibleCluster = final.toString() + char
                if (mergedClusters.containsKey(possibleCluster)) {
                    final2 = char
                    continue
                } // if it's a valid second final, add it
                
                if (isVowel(char)) {
                    combined.append(toBlock(initial, vowel, null))
                    initial = final
                    vowel = char
                    final = null
                    continue
                } // if char is a vowel, start a new syllable, taking the final from the existing
                // syllable together with the current char (vowel)

                if (isInitial(char)) {
                    combined.append(toBlock(initial, vowel, final))
                    initial = char
                    vowel = null
                    final = null
                    continue
                } // after the double final case has been handled earlier, if char is an initial,
                // start a new syllable with it

                combined.append(char) // if it's neither a vowel nor an initial, just output it
                continue
            }

            // Last case, now there is a vowel, initial, and both finals
            if (isVowel(char)) {
                combined.append(toBlock(initial, vowel, final))
                initial = final2
                vowel = char
                final = null
                final2 = null
                continue
            } // same vowel case as above, but now take only the second final for a new syllable
            // instead of the first final

            if (isInitial(char)) {
                val finalCluster = mergedClusters[final.toString() + final2]!!
                combined.append(toBlock(initial, vowel, finalCluster))
                initial = char
                vowel = null
                final = null
                final2 = null
                continue
            } // if char is an initial, start a new syllable
            combined.append(char) // if it's neither an initial nor a vowel, just output it
            continue
        }

        // Final iteration: output whatever is in initial, vowel and final variables
        if (initial != null && vowel == null && final == null && final2 == null) {
            combined.append(initial)
        }
        if (initial != null && vowel != null && final2 == null) {
            combined.append(toBlock(initial, vowel, final))
        }
        if (final2 != null) {
            val finalCluster = mergedClusters[final.toString() + final2]!!
            combined.append(toBlock(initial!!, vowel!!, finalCluster))
        }
        return combined
    }

    override fun processEvent(previousEvents: ArrayList<Event>?, event: Event?): Event {
        if (event == null) return Event.createNotHandledEvent()
        val keypress = event.mCodePoint.toChar()

        if (!isLetter(keypress)) {
            if (!TextUtils.isEmpty(buffer)) {
                if (event.mKeyCode == Constants.CODE_DELETE) {
                    if (buffer.length == 1) {
                        reset()
                        return Event.createHardwareKeypressEvent(0x20, Constants.CODE_SPACE,
                            event, event.isKeyRepeat)
                        // for some reason, this is needed, otherwise if there is only one letter
                        // in the buffer it won't be deleted
                    }
                    else {
                        buffer.setLength(buffer.length - 1)
                        return Event.createConsumedEvent(event)
                    }
                }
            }
            return event
        }

        buffer.append(keypress)
        return Event.createConsumedEvent(event)
    }

    override fun getCombiningStateFeedback(): CharSequence {
        return toBlocks()
    }

    override fun reset() {
        buffer.setLength(0)
    }
}