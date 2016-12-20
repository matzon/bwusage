package dk.matzon.bwusage.domain.model;

import javax.persistence.Entity;
import javax.persistence.Id;
import java.io.Serializable;
import java.util.Date;
import java.util.Objects;

/**
 * Created by Brian Matzon <brian@matzon.dk>
 */
@Entity
public class BWEntry implements Serializable {

    @Id
    private Date date;
    private String upload;
    private String download;

    public BWEntry() {
    }

    public BWEntry(Date _date, String _upload, String _download) {
        date = _date;
        upload = _upload;
        download = _download;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date _date) {
        date = _date;
    }

    public String getUpload() {
        return upload;
    }

    public void setUpload(String _upload) {
        upload = _upload;
    }

    public String getDownload() {
        return download;
    }

    public void setDownload(String _download) {
        download = _download;
    }

    @Override
    public boolean equals(Object _o) {
        if (this == _o) return true;
        if (_o == null || getClass() != _o.getClass()) return false;
        BWEntry bwEntry = (BWEntry) _o;
        return Objects.equals(date, bwEntry.date) &&
                Objects.equals(upload, bwEntry.upload) &&
                Objects.equals(download, bwEntry.download);
    }

    @Override
    public int hashCode() {
        return Objects.hash(date, upload, download);
    }

    @Override
    public String toString() {
        return "BWEntry{" +
                "date=" + date +
                ", upload='" + upload + '\'' +
                ", download='" + download + '\'' +
                '}';
    }
}
