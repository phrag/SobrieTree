package com.brewlog.android.engine

data class EducationCard(
    val id: String,
    val title: String,
    val summary: String,
    val body: String,
    val source: String,
    /** Motivation tags this card is most relevant to (sleep, health, money, weight, mind). */
    val tags: Set<String>
)

/**
 * Short, evidence-based reads for the Journey tab. All offline; each card
 * carries a one-line attribution. Ordered per user by their chosen motivations.
 */
object EducationLibrary {

    val cards: List<EducationCard> = listOf(
        EducationCard(
            "guidance",
            "Alcohol & your health: the guidance",
            "Health authorities agree — less is better, and there is no risk-free level.",
            "The World Health Organization states that no level of alcohol consumption is safe for our health, and the International Agency for Research on Cancer classifies alcohol as a Group 1 carcinogen — the highest category, alongside tobacco — causally linked to at least seven cancers, including breast and bowel. For those who do drink, the UK Chief Medical Officers' low-risk guidelines (used by the NHS) advise no more than 14 units a week — about 6 pints of average-strength beer or 10 small glasses of wine — spread over three or more days, with several drink-free days each week. Because risk rises with the amount, every drink you skip lowers it.",
            "WHO/Europe (2023): no safe level. IARC: alcohol is a Group 1 carcinogen. NHS — UK Chief Medical Officers' low-risk drinking guidelines (2016): ≤14 units/week, spread over 3+ days.",
            setOf("health", "sleep", "money", "weight", "mind")
        ),
        EducationCard(
            "sleep_architecture",
            "Why the nightcap backfires",
            "Alcohol makes you fall asleep faster but wrecks the second half of the night.",
            "Alcohol is sedating at first, so it feels like a sleep aid. But as your body metabolises it overnight, it suppresses REM sleep and causes fragmented, shallow sleep in the early morning hours. That's why a night with drinks often ends with waking at 4 a.m. After even a few alcohol-free nights, most people notice they wake less and dream more - a sign REM sleep is recovering.",
            "Based on sleep research summarised by the Sleep Foundation and NHS guidance.",
            setOf("sleep")
        ),
        EducationCard(
            "liver_recovery",
            "Your liver's comeback story",
            "The liver starts repairing within days of drinking less.",
            "The liver is one of the few organs that regenerates. Fat accumulation in the liver - the first stage of alcohol-related damage - begins reversing within days of cutting back, and studies of month-long breaks show measurable drops in liver fat. For moderate drinkers, liver function markers can return to a healthy range within about six months of drinking less.",
            "Based on a University of Sussex 'Dry January' study (Alcohol Change UK) and NHS guidance on liver health.",
            setOf("health")
        ),
        EducationCard(
            "blood_pressure",
            "The blood pressure dividend",
            "A month of drinking less measurably lowers blood pressure.",
            "Regular drinking raises blood pressure - one of the biggest silent risk factors for heart disease and stroke. In a study of people taking a month off alcohol, blood pressure fell by around 6% on average, and insulin resistance dropped by about 25%. These changes start within weeks, not years.",
            "Based on a Royal Free Hospital / UCL study of month-long alcohol breaks.",
            setOf("health")
        ),
        EducationCard(
            "calories",
            "The invisible calories",
            "Alcohol is nearly as calorie-dense as pure fat.",
            "Alcohol carries about 7 kcal per gram - close to fat's 9 and far above sugar's 4. A large beer is roughly a burger's worth of calories, and unlike food calories, alcohol calories don't trigger fullness. Cutting a few drinks a week often shows up on the scale within a month, without any other change.",
            "Based on standard nutrition values used by the NHS and Alcohol Change UK.",
            setOf("weight", "money")
        ),
        EducationCard(
            "hangxiety",
            "Where 'hangxiety' comes from",
            "The morning-after dread is chemistry, not character.",
            "Alcohol boosts GABA (calming) and suppresses glutamate (stimulating). Overnight, your brain rebalances by doing the opposite - so you wake with the dial turned toward anxiety. Regular drinking keeps this rebound cycle running. People who cut back consistently report steadier mood and lower baseline anxiety within a few weeks.",
            "Based on neuroscience research on alcohol's GABA/glutamate rebound.",
            setOf("mind")
        ),
        EducationCard(
            "urge_surfing",
            "Urge surfing: the 10-minute rule",
            "Cravings crest and fall like a wave - usually within minutes.",
            "A craving feels permanent but behaves like a wave: it builds, peaks, and subsides, typically within 10-20 minutes. Instead of fighting it, note it ('a craving is here'), set a 10-minute timer and do something with your hands. Most urges pass before the timer does. Each surfed urge weakens the next one.",
            "Based on urge-surfing techniques from mindfulness-based relapse prevention.",
            setOf("mind")
        ),
        EducationCard(
            "precommitment",
            "Decide before the evening does",
            "Planning your drinks in advance beats willpower in the moment.",
            "Willpower is weakest exactly when the round is being ordered. People who decide in advance - which days are alcohol-free, how many drinks on the others - cut consumption far more reliably than people who 'play it by ear'. Use your weekly goal as the plan, and treat alcohol-free days as appointments.",
            "Based on behavioural-science research on pre-commitment and NIAAA guidance on planning ahead to drink less.",
            setOf("mind", "health")
        ),
        EducationCard(
            "social_scripts",
            "Saying no without a speech",
            "A ready answer makes declining a drink a non-event.",
            "Most people don't actually care what's in your glass - they care that you're there. Have one line ready: 'I'm driving', 'Early start tomorrow', 'I'm on a health kick this month', or simply order first ('a soda water with lime'). Holding any drink ends the conversation before it starts.",
            "Based on practical guidance from Alcohol Change UK.",
            setOf("mind")
        ),
        EducationCard(
            "af_days",
            "The power of alcohol-free days",
            "Consecutive drink-free days are where recovery happens.",
            "Health guidelines converge on one practical rule: several alcohol-free days every week. They give your liver time to repair, break the daily-habit loop, and reset your tolerance so the drinks you do have work harder. Two or more AF days a week is the single highest-leverage change most regular drinkers can make.",
            "Based on UK Chief Medical Officers' low-risk drinking guidelines.",
            setOf("health", "sleep")
        ),
        EducationCard(
            "lapse_not_relapse",
            "A lapse is not a relapse",
            "One heavy night is a data point, not a verdict.",
            "Research on habit change is unambiguous: nearly everyone who successfully cuts back has off days along the way. What separates people who succeed is not perfection - it's how quickly they return to the plan. Log the heavy day honestly, skip the self-criticism (it demonstrably makes things worse), and treat the next day as the streak's first day, not the failure's second.",
            "Based on the abstinence-violation-effect literature in behaviour change research.",
            setOf("mind", "health")
        )
    )

    /** Cards re-ordered so the user's motivations come first. */
    fun orderedFor(motivations: Set<String>): List<EducationCard> =
        if (motivations.isEmpty()) cards
        else cards.sortedByDescending { card -> card.tags.count { it in motivations } }
}
