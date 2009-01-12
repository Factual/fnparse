(ns name.choi.joshua.fnparse)

; A rule is a delay object that contains a function that:
; - Takes a collection of tokens.
; - If the token sequence is valid, it returns a vector containing the (0) consumed symbols'
;   products and (1) a sequence of the remaining symbols or nil. In all documentation here,
;   "a rule's products" is the first element of a valid result from the rule.
; - If the given token sequence is invalid and the rule fails, it simply returns nil.

(defn term
  "Creates a rule that is a terminal rule of the given validator--that is, it accepts only
  tokens for whom (validator token) is true.
  (def a (term validator)) would be equivalent to the EBNF
    a = ? (validator %) evaluates to true ?;
  The new rule's product would be the first token, if it fulfills the validator.
  If the token does not fulfill the validator, the new rule simply returns nil."
  [validator]
  (fn [tokens]
    (let [first-token (first tokens)]
      (if (validator first-token),
          [first-token (rest tokens)]))))

(defn semantics
  "Creates a rule function from attaching a semantic hook function to the given subrule--
  that is, its products are from applying the semantic hook to the subrule's products. When
  the subrule fails and returns nil, the new rule will return nil."
  [subrule semantic-hook]
  (fn [tokens]
      (let [subrule-result (subrule tokens)]
        (if (not (nil? subrule-result))
            [(semantic-hook (subrule-result 0)) (subrule-result 1)]))))

(defn constant-semantics
  "Creates a rule function from attaching a constant semantic hook function to the given
  subrule--that is, its product is a constant value. When the subrule fails and returns nil,
  the new rule will return nil."
  [subrule semantic-value]
  (semantics subrule (fn [_] semantic-value)))

(defn lit
  "Creates a rule function that is the terminal rule of the given literal token--that is, it
  accepts only tokens that are equal to the given literal token.
  (def a (lit \"...\")) would be equivalent to the EBNF
    a = \"...\";
  The new rule's product would be the first token, if it equals the given literal token.
  If the token does not equal the given literal token, the new rule simply returns nil."
  [literal-token]
  (term #(= % literal-token)))

(defn re-term
  "Creates a rule function that is the terminal rule of the given regex--that is, it accepts
  only tokens that match the given regex.
  (def a (re-term #\"...\")) would be equivalent to the EBNF
    a = ? (re-matches #\"...\" %) evaluates to true ?;
  The new rule's product would be the first token, if it matches the given regex.
  If the token does not match the given regex, the new rule simply returns nil."
  [token-regex]
  (term #(re-matches token-regex %)))

(defn conc
  "Creates a rule function that is the concatenation of the given subrules--that is, each
  subrule followed by the next.
  (def a (conc b c d)) would be equivalent to the EBNF
    a = b, c, d;
  The new rule's products would be the vector [b-product c-product d-product]. If any of
  the subrules don't match in the right place, the new rule simply returns nil."
  [& subrules]
  (fn [tokens]
    (loop [products [], token-queue tokens, rule-queue subrules]
      (if (nil? rule-queue),
          [products token-queue],
          (let [curr-result ((first rule-queue) token-queue)]
            (if (not (nil? curr-result))
                (recur (conj products (curr-result 0))
                       (curr-result 1)
                       (rest rule-queue))))))))

(defn alt
  "Creates a rule function that is the alternative of the given subrules--that is, any one
  of the given subrules. Note that the subrules' order matters: the very first rule that
  accepts the given tokens will be selected.
  (def a (alt b c d)) would be equivalent to the EBNF
    a = b | c | d;
  The new rule's product would be b-product, c-product, or d-product depending on which
  of the rules first accepts the given tokens. If none of the subrules matches, the new rule
  simply returns nil."
  [& subrules]
  (fn [tokens]
    (some #(% tokens) subrules)))

(defn opt
  "Creates a rule function that is the optional form of the given subrule--that is, either
  the presence of the absence of the subrule.
  (def a (opt b)) would be equivalent to the EBNF
    a = [b];
  The new rule's product would be either a-product, if a accepts it, or else nil. Note
  that the latter actually means that the new rule would then return the vector
  [nil tokens]. The new rule can never simply return nil."
  [subrule]
  (fn [tokens]
    (or (subrule tokens) [nil tokens])))

(defn rep*
  "Creates a rule function that is the zero-or-more repetition of the given subrule--that
  is, either zero or more of the subrule.
  (def a (rep* b)) would be equivalent to the EBNF
    a = {b};
  The new rule's products would be either the vector [b-product ...] for how many matches
  of b were found, or the empty vector [] if there was no match. Note that the latter
  actually means that the new rule would then return the vector [[] tokens]. The new rule
  can never simply return nil."
  [subrule]
  (fn [tokens]
    (loop [products [], token-queue tokens]
      (let [cur-result (subrule token-queue)]
        (if (or (nil? cur-result) (= cur-result [nil nil])),
            [products token-queue],
            (recur (conj products (cur-result 0))
                   (cur-result 1)))))))

(defn rep+
  "Creates a rule function that is the one-or-more repetition of the given rule--that
  is, either one or more of the rule.
  (def a (rep+ b)) would be equivalent to the EBNF
    a = {b}-;
  The new rule's products would be the vector [b-product ...] for how many matches of b
  were found. If there were zero matches, then nil is simply returned."
  [subrule]
  (fn [tokens]
    (let [product ((rep* subrule) tokens)]
       (if (not (empty? (product 0))), product))))

(defn lit-seq
  "Creates a rule function that is the concatenation of the literals of the sequence of the
  given sequenceable object--that is, it accepts only a series of tokens that matches the
  sequence of the token sequence.
  (def a (lit-seq \"ABCD\")) would be equivalent to the EBNF
    a = \"A\", \"B\", \"C\", \"D\";
  The new rule's products would be the result of the concatenation rule."
  [token-seq]
  (semantics (apply conc (map lit token-seq)) seq))

(defn emptiness
  "A rule function that matches emptiness--that is, it always matches with every given token
  sequence, and it always returns [nil tokens]."
  [tokens]
  [nil tokens])

(defn flatten
  "Takes any nested combination of sequential things (lists, vectors,
  etc.) and returns their contents as a single, flat sequence."
  [x]
  (let [s? #(instance? clojure.lang.Sequential %)]
    (filter (complement s?) (tree-seq s? seq x))))
