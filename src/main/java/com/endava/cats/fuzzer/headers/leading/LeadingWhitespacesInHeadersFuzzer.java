package com.endava.cats.fuzzer.headers.leading;

import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.annotations.WhitespaceFuzzer;
import com.endava.cats.fuzzer.headers.base.InvisibleCharsBaseFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.model.util.PayloadUtils;

import javax.inject.Singleton;
import java.util.List;

@Singleton
@HeaderFuzzer
@WhitespaceFuzzer
public class LeadingWhitespacesInHeadersFuzzer extends InvisibleCharsBaseFuzzer {

    public LeadingWhitespacesInHeadersFuzzer(ServiceCaller sc, TestCaseListener lr) {
        super(sc, lr);
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "prefix value with unicode separators";
    }

    @Override
    public List<String> getInvisibleChars() {
        return PayloadUtils.getSeparatorsHeaders();
    }

    @Override
    public FuzzingStrategy concreteFuzzStrategy() {
        return FuzzingStrategy.prefix();
    }
}
