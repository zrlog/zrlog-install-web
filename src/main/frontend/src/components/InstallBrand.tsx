import {getRes} from "../utils/constants";
import ZrLogMark from "./ZrLogMark";

const InstallBrand = () => (
    <div className="install-brand">
        <ZrLogMark/>
        <div className="install-brand-copy">
            <span className="install-brand-name">ZrLog</span>
            <span className="install-brand-caption">{getRes().wizard.title}</span>
        </div>
    </div>
);

export default InstallBrand;
