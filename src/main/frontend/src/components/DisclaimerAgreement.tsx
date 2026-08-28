import React from 'react';
import {Typography} from "antd";
import {getRes} from "../utils/constants";
import {renderSanitizedMarkdown} from "../utils/sanitize-html";

const DisclaimerAgreement: React.FC = () => {
    const content = renderSanitizedMarkdown(getRes().agreement.content);

    return (
        <Typography className="install-disclaimer-content"
                    dangerouslySetInnerHTML={{__html: content}}/>
    );
};


export default DisclaimerAgreement;
